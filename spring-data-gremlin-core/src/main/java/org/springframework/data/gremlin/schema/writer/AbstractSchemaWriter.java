package org.springframework.data.gremlin.schema.writer;

import com.tinkerpop.blueprints.Direction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.gremlin.schema.GremlinSchema;
import org.springframework.data.gremlin.schema.property.GremlinAdjacentProperty;
import org.springframework.data.gremlin.schema.property.GremlinProperty;
import org.springframework.data.gremlin.schema.property.GremlinRelatedProperty;
import org.springframework.data.gremlin.tx.GremlinGraphFactory;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import static org.springframework.data.gremlin.schema.property.GremlinRelatedProperty.CARDINALITY;

/**
 * An abstract {@link SchemaWriter} for implementing databases to extend for easy integration.
 *
 * @author Gman
 */
public abstract class AbstractSchemaWriter<V extends BASE, E extends BASE, P, BASE> implements SchemaWriter {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractSchemaWriter.class);
    private Set<GremlinSchema<?>> handledSchemas = new HashSet<>();

    @Override
    public void writeSchema(GremlinGraphFactory tgf, GremlinSchema<?> schema) throws SchemaWriterException {
        if (handledSchemas.contains(schema)) {
            return;
        }
        if (schema.getSuperSchema() != null) {
            writeSchema(tgf, schema.getSuperSchema());
        }
        try {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("CREATING CLASS: " + schema.getClassName());
            }
            BASE element;
            if (schema.isVertexSchema()) {
                element = createVertexClass(schema);
            } else if (schema.isEdgeSchema()) {

                V outVertex = createVertexClass(schema.getOutProperty().getRelatedSchema());
                V inVertex = createVertexClass(schema.getInProperty().getRelatedSchema());

                element = createEdgeClass(schema.getClassName(), outVertex, inVertex, schema.getOutProperty().getCardinality());
            } else {
                throw new IllegalStateException("Unknown class type. Expected Vertex or Edge. " + schema);
            }

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("CREATED CLASS: " + schema.getClassName());
            }
            writeProperties(element, schema);
            handledSchemas.add(schema);
        } catch (Exception e) {

            rollback(schema);

            String msg = String.format("Could not create schema %s. ERROR: %s", schema, e.getMessage());
            LOGGER.error(e.getMessage(), e);
            throw new SchemaWriterException(msg, e);
        }
    }

    private void writeProperties(BASE elementClass, GremlinSchema<?> schema) {
        GremlinProperty latitude = null;
        GremlinProperty longitude = null;
        for (Iterator<GremlinProperty> iterator = schema.getPropertyStream().iterator(); iterator.hasNext(); ) {
            GremlinProperty property = iterator.next();

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("CREATING Property: " + property.getName());
            }
            Class<?> cls = property.getType();

            try {

                // If prop is null, it does not exist, so let's create it
                if (!isPropertyAvailable(elementClass, property.getName())) {

                    if (property instanceof GremlinAdjacentProperty) {
                        continue;
                    }

                    if (property instanceof GremlinRelatedProperty) {

                        GremlinRelatedProperty relatedProperty = (GremlinRelatedProperty) property;
                        if (relatedProperty.getRelatedSchema().isVertexSchema()) {

                            if (LOGGER.isDebugEnabled()) {
                                LOGGER.debug("CREATING RELATED PROPERTY: " + schema.getClassName() + "." + property.getName());
                            }
                            V relatedVertex = createVertexClass(relatedProperty.getRelatedSchema());

                            if (((GremlinRelatedProperty) property).getDirection() == Direction.OUT) {
                                //noinspection unchecked
                                createEdgeClass(property.getName(), (V) elementClass, relatedVertex, relatedProperty.getCardinality());
                            } else {
                                //noinspection unchecked
                                createEdgeClass(property.getName(), relatedVertex, (V) elementClass, relatedProperty.getCardinality());
                            }
                        } else {
                            if (LOGGER.isDebugEnabled()) {
                                LOGGER.debug("CREATING RELATED EDGE: " + schema.getClassName() + "." + property.getName());
                            }
                            V relatedVertex = createVertexClass(relatedProperty.getAdjacentProperty().getRelatedSchema());

                            if (((GremlinRelatedProperty) property).getDirection() == Direction.OUT) {
                                //noinspection unchecked
                                createEdgeClass(relatedProperty.getRelatedSchema().getClassName(), (V) elementClass, relatedVertex, relatedProperty.getCardinality());
                            } else {
                                //noinspection unchecked
                                createEdgeClass(relatedProperty.getRelatedSchema().getClassName(), relatedVertex, (V) elementClass, relatedProperty.getCardinality());
                            }
                        }

                    } else {

                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("CREATING PROPERTY: " + schema.getClassName() + "." + property.getName());
                        }
                        // Standard property, primitive, String, Enum, byte[]
                        P prop = createProperty(elementClass, property.getName(), cls);

                        switch (property.getIndex()) {
                            case UNIQUE:
                                createUniqueIndex(prop);
                                break;
                            case NON_UNIQUE:
                                createNonUniqueIndex(prop);
                                break;
                            case SPATIAL_LATITUDE:
                                latitude = property;
                                break;

                            case SPATIAL_LONGITUDE:
                                longitude = property;
                                break;
                        }
                    }
                }

                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("CREATED CLASS: " + schema.getClassName());
                }
            } catch (Exception e1) {
                LOGGER.warn(String.format("Could not create property %s of type %s", property, cls), e1);
            }
        }

        if (latitude != null && longitude != null) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("CREATING SPATIAL INDEX...");
            }
            createSpatialIndex(schema, latitude, longitude);

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("CREATED SPATIAL INDEX.");
            }
        }
    }


    protected abstract boolean isPropertyAvailable(Object vertexClass, String name);

    protected abstract V createVertexClass(GremlinSchema schema) throws Exception;

    protected abstract E createEdgeClass(GremlinSchema schema) throws Exception;

    protected abstract void rollback(GremlinSchema schema);

    protected abstract E createEdgeClass(String name, V outVertex, V inVertex, CARDINALITY cardinality) throws SchemaWriterException;

    protected abstract boolean isEdgeInProperty(E edgeClass);

    protected abstract boolean isEdgeOutProperty(E edgeClass);

    protected abstract P setEdgeOut(E edgeClass, V vertexClass);

    protected abstract P setEdgeIn(E edgeClass, V vertexClass);

    protected abstract P createProperty(BASE parentElement, String name, Class<?> cls);

    protected abstract void createNonUniqueIndex(P prop);

    protected abstract void createUniqueIndex(P prop);

    protected abstract void createSpatialIndex(GremlinSchema<?> schema, GremlinProperty latitude, GremlinProperty longitude);

}
