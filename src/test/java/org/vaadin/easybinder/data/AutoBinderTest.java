package org.vaadin.easybinder.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import javax.validation.constraints.Min;

import org.junit.Test;
import org.vaadin.easybinder.data.AutoBinder;

import com.vaadin.data.HasValue;
import com.vaadin.ui.AbstractField;

public class AutoBinderTest {
		
	public static class Car {
		Wheel frontLeft = new Wheel();
		
		public Wheel getFrontLeft() {
			return frontLeft;
		}
		
		public void setFrontLeft(Wheel frontLeft) {
			this.frontLeft = frontLeft;
		}
	}
	
	public static class Wheel {		
		Tire tire = new Tire();
		
		public Tire getTire() {
			return tire;
		}
		
		public void setTire(Tire tire) {
			this.tire = tire;
		}
	}
	
	public static class Tire {
		String type;
		
		public String getType() {
			return type;
		}
		
		public void setType(String type) {
			this.type = type;
		}
	}	
	
	public static class Unknown {
		String name = "aaa";
		int id = 10;
		
		@Override
		public String toString() {
			return name+id;
		}
	}
	
	
	public static class MyEntity {
		String street;

		@Min(value = 1)
		Integer number;

		int number2;
		
		Car car = new Car();
		
		Wheel spare = new Wheel();

		int numberReadOnly;
		
		Unknown unknown = new Unknown();
		
		public String getStreet() {
			return street;
		}

		public void setStreet(String street) {
			this.street = street;
		}

		public Integer getNumber() {
			return number;
		}

		public void setNumber(Integer number) {
			this.number = number;
		}

		public int getNumber2() {
			return number2;
		}

		public void setNumber2(int number) {
			this.number2 = number;
		}
		
		public Car getCar() {
			return car;
		}
		
		public void setCar(Car car) {
			this.car = car;
		}
		
		public Wheel getSpare() {
			return spare;
		}
		
		public void setSpare(Wheel spare) {
			this.spare = spare;
		}

		public int getNumberReadOnly() {
			return numberReadOnly;
		}
		
		public Unknown getUnknown() {
			return unknown;
		}
		
		public void setUnknown(Unknown unknown) {
			this.unknown = unknown;
		}
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void testBuildAndBind() {
		AutoBinder<MyEntity> binder = new AutoBinder<>(MyEntity.class);
		binder.buildAndBind("car", "car.frontLeft", "car.frontLeft.tire", "spare", "spare.tire");

		MyEntity entity = new MyEntity();
		binder.setBean(entity);

		assertTrue(binder.getFieldForProperty("street").isPresent());
		assertTrue(binder.getFieldForProperty("number").isPresent());
		assertTrue(binder.getFieldForProperty("number2").isPresent());
		assertTrue(binder.getFieldForProperty("car.frontLeft.tire.type").isPresent());
		assertTrue(binder.getFieldForProperty("spare.tire.type").isPresent());
		assertTrue(binder.getFieldForProperty("unknown").isPresent());
		

		AbstractField<String> numberField = (AbstractField<String>) binder.getFieldForProperty("number").get();
		AbstractField<String> numberField2 = (AbstractField<String>) binder.getFieldForProperty("number2").get();

		((HasValue<String>) binder.getFieldForProperty("street").get()).setValue("mystreet");
		assertEquals("mystreet", entity.getStreet());

		numberField.setValue("100");
		assertEquals(new Integer(100), entity.getNumber());

		assertNull(numberField.getComponentError());
		numberField.setValue("0");
		assertNotNull(numberField.getComponentError());
		numberField.setValue("");

		assertEquals(null, entity.getNumber());
		assertNull(numberField.getComponentError());

		numberField2.setValue("");
		assertNotNull(numberField2.getComponentError());
	}	
	
}
