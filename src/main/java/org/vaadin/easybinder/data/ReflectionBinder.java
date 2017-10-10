/*
 * Copyright 2017 Lars Sønderby Jessen
 * 
 * Partly based on code copied from Vaadin Framework (Binder)
 * Copyright 2000-2016 Vaadin Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.vaadin.easybinder.data;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.validation.constraints.Min;

import org.vaadin.easybinder.data.converters.NullConverter;

import com.googlecode.gentyref.GenericTypeReflector;
import com.vaadin.data.BeanPropertySet;
import com.vaadin.data.Converter;
import com.vaadin.data.HasValue;
import com.vaadin.data.PropertyDefinition;
import com.vaadin.data.PropertySet;
import com.vaadin.data.RequiredFieldConfigurator;
import com.vaadin.data.ValueProvider;
import com.vaadin.server.Setter;
import com.vaadin.util.ReflectTools;

public class ReflectionBinder<BEAN> extends BasicBinder<BEAN> implements HasGenericType<BEAN> {
	protected Class<BEAN> clazz;

	protected PropertySet<BEAN> propertySet;

	protected Map<String, EasyBinding<BEAN, ?, ?>> boundProperties = new HashMap<String, EasyBinding<BEAN, ?, ?>>();

	protected static ConverterRegistry globalConverterRegistry = ConverterRegistry.getInstance();

	private static RequiredFieldConfigurator MIN = annotation -> annotation.annotationType().equals(Min.class)
			&& ((Min) annotation).value() > 0;

	protected Logger log = Logger.getLogger(getClass().getName());

	protected RequiredFieldConfigurator requiredConfigurator = MIN.chain(RequiredFieldConfigurator.DEFAULT);

	public ReflectionBinder(Class<BEAN> clazz) {
		this.clazz = clazz;
		propertySet = BeanPropertySet.get(clazz);
	}

	public <PRESENTATION, MODEL> EasyBinding<BEAN, PRESENTATION, MODEL> bind(HasValue<PRESENTATION> field,
			String propertyName) {

		boolean readOnly = false;
		
		Objects.requireNonNull(propertyName, "Property name cannot be null");
		// checkUnbound();

		PropertyDefinition<BEAN, ?> definition = propertySet.getProperty(propertyName)
				.orElseThrow(() -> new IllegalArgumentException(
						"Could not resolve property name " + propertyName + " from " + propertySet));

		Optional<Class<PRESENTATION>> fieldTypeClass = getFieldTypeForField(field);

		Class<?> modelTypeClass = definition.getType();

		// Hack as PropertyDefinition does not return primitive type
		Optional<Field> modelField = getDeclaredFieldByName(definition.getPropertyHolderType(), definition.getName());
		if (modelField.isPresent()) {
			modelTypeClass = modelField.get().getType();
		}

		Converter<PRESENTATION, ?> converter = null;
		if (fieldTypeClass.isPresent()) {
			converter = globalConverterRegistry.getConverter(fieldTypeClass.get(), modelTypeClass);
			if (converter != null) {
				log.log(Level.INFO, "Converter for {0}->{1} found by lookup",
						new Object[] { fieldTypeClass.get(), modelTypeClass });
			} else if (fieldTypeClass.get().equals(modelTypeClass)) {
				if (modelTypeClass.isPrimitive()) {
					converter = Converter.identity();
					log.log(Level.INFO, "Converter for primitive {0}->{1} found by identity",
							new Object[] { fieldTypeClass.get(), modelTypeClass });
				} else {
					converter = new NullConverter<PRESENTATION>(field.getEmptyValue());
					log.log(Level.INFO, "Converter for non-primitive {0}->{1} found by identity",
							new Object[] { fieldTypeClass.get(), modelTypeClass });
				}
			} else if (ReflectTools.convertPrimitiveType(fieldTypeClass.get()).equals(ReflectTools.convertPrimitiveType(modelTypeClass))) {
				log.log(Level.INFO, "Converter for {0}->{1} found by assignment",
						new Object[] { fieldTypeClass.get(), modelTypeClass });
				converter = createConverter(definition.getType());
			} else if (fieldTypeClass.get().equals(String.class)) {
				log.log(Level.INFO, "Unable to find converter between presentationType=<{0}> and modelType=<{1}>, using read-only toString() converter",
						new Object[] { fieldTypeClass.get(), modelTypeClass });
				converter = createStringConverter();
				readOnly = true;
			} else {
				log.log(Level.WARNING, "Unable to find converter between presentationType=<{0}> and modelType=<{1}>. Please register a converter. Using default assignment converter",
						new Object[] { fieldTypeClass.get(), modelTypeClass });
				converter = createConverter(definition.getType());
			}
		} 

		if(converter == null) {
			log.log(Level.WARNING, "Unable to determine presentation type of field due to type-erasure. Fields requiring generic type arguments should either implement HasGenericType, be wrapped by EGTypeComponentAdapter or be subclassed to ensure type can be recovered. Using default assignment converter for propertyType=<{0}>", new Object[] { modelTypeClass });

			// Uses definition.getType() to ensure that the object type and not primitive type is returned.
			converter = createConverter(definition.getType());
		}

		return bind(field, propertyName, converter, readOnly);
	}

	public <PRESENTATION, MODEL> EasyBinding<BEAN, PRESENTATION, MODEL> bind(HasValue<PRESENTATION> field,
			String propertyName, Converter<PRESENTATION, ?> converter) {
		return bind(field, propertyName, converter, false);
	}
	
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public <PRESENTATION, MODEL> EasyBinding<BEAN, PRESENTATION, MODEL> bind(HasValue<PRESENTATION> field,
			String propertyName, Converter<PRESENTATION, ?> converter, boolean readOnly) {
		Objects.requireNonNull(converter);
		Objects.requireNonNull(propertyName, "Property name cannot be null");
		// checkUnbound();
		
		PropertyDefinition<BEAN, ?> definition = propertySet.getProperty(propertyName)
				.orElseThrow(() -> new IllegalArgumentException(
						"Could not resolve property name " + propertyName + " from " + propertySet));

		ValueProvider<BEAN, ?> getter = definition.getGetter();
		Setter<BEAN, ?> setter = readOnly ? null : definition.getSetter().orElse(null);

		EasyBinding<BEAN, PRESENTATION, MODEL> binding = bind(field, (ValueProvider) getter, (Setter) setter,
				propertyName, (Converter) converter);

		boundProperties.put(propertyName, binding);

		Optional<Field> modelField = getDeclaredFieldByName(definition.getPropertyHolderType(), definition.getName());
		if (modelField.isPresent()) {
			if (Arrays.asList(modelField.get().getAnnotations()).stream().anyMatch(requiredConfigurator)) {
				field.setRequiredIndicatorVisible(true);
			}
		}

		return binding;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	protected <PRESENTATION, MODEL> Converter<PRESENTATION, MODEL> createConverter(Class<MODEL> propertyType) {
		return (Converter) Converter.from(fieldValue -> propertyType.cast(fieldValue),
				propertyValue -> (MODEL) propertyValue, exception -> {
					throw new RuntimeException(exception);
				});
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	protected <PRESENTATION, MODEL> Converter<PRESENTATION, MODEL> createStringConverter() {
		return (Converter) Converter.from(null, fieldValue -> fieldValue.toString(), exception -> {
					throw new RuntimeException(exception);
				});
	}	
	
	
	@SuppressWarnings("unchecked")
	protected <PRESENTATION> Optional<Class<PRESENTATION>> getFieldTypeForField(HasValue<PRESENTATION> field) {
		// Try to find the field type using reflection
		Type valueType = GenericTypeReflector.getTypeParameter(field.getClass(), HasValue.class.getTypeParameters()[0]);
		if(valueType != null) {
			return Optional.of((Class<PRESENTATION>) valueType);
		}

		// Not possible to find using reflection (due to type erasure)
		// If field is an instance of HasGenericType
		if(field instanceof HasGenericType) {
			HasGenericType<PRESENTATION> type = (HasGenericType<PRESENTATION>)field;
			return Optional.of(type.getGenericType());
		}

		return Optional.empty();
	}

	protected Optional<Field> getDeclaredFieldByName(Class<?> searchClass, String name) {
		while (searchClass != null) {
			try {
				return Optional.of(searchClass.getDeclaredField(name));
			} catch (NoSuchFieldException | SecurityException e) {
				// No such field, try superclass
				searchClass = searchClass.getSuperclass();
			}
		}
		return Optional.empty();
	}

	/**
	 * Sets a logic which allows to configure require indicator via
	 * {@link HasValue#setRequiredIndicatorVisible(boolean)} based on property
	 * descriptor.
	 * <p>
	 * Required indicator configuration will not be used at all if
	 * {@code configurator} is null.
	 * <p>
	 * By default the {@link RequiredFieldConfigurator#DEFAULT} configurator is
	 * used.
	 *
	 * @param configurator
	 *            required indicator configurator, may be {@code null}
	 */
	public void setRequiredConfigurator(RequiredFieldConfigurator configurator) {
		requiredConfigurator = configurator;
	}

	/**
	 * Gets field required indicator configuration logic.
	 *
	 * @see #setRequiredConfigurator(RequiredFieldConfigurator)
	 *
	 * @return required indicator configurator, may be {@code null}
	 */
	public RequiredFieldConfigurator getRequiredConfigurator() {
		return requiredConfigurator;
	}

	@Override
	public Class<BEAN> getGenericType() {
		return clazz;
	}

}