package org.springframework.data.gremlin.object.neo4j.domain;

import org.springframework.data.neo4j.annotation.NodeEntity;

/**
 * @author <a href="mailto:andreas.berger@kiwigrid.com">Andreas Berger</a>
 */
@NodeEntity
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

	public Integer getSize() {
		return size;
	}

	public ExtendedPerson setSize(Integer size) {
		this.size = size;
		return this;
	}
}
