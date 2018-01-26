package org.springframework.data.gremlin.object.core.domain;

import org.springframework.data.gremlin.annotation.Vertex;

import java.util.Map;

/**
 * @author <a href="mailto:andreas.berger@kiwigrid.com">Andreas Berger</a>
 */
@Vertex
public class ExtendedPerson extends Person {
	private Integer size;

	public ExtendedPerson() {
	}

	public ExtendedPerson(String firstName, String lastName) {
		super(firstName, lastName);
	}

	public ExtendedPerson(String firstName, String lastName, Address address, Boolean active) {
		super(firstName, lastName, address, active);
	}

	public ExtendedPerson(String firstName, String lastName, Address address, Boolean active, Map<String, Object> randoms) {
		super(firstName, lastName, address, active, randoms);
	}

	public Integer getSize() {
		return size;
	}

	public ExtendedPerson setSize(Integer size) {
		this.size = size;
		return this;
	}
}
