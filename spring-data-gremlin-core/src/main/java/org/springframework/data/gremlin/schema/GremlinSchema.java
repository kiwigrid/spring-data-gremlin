package org.springframework.data.gremlin.schema;

import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;
import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.blueprints.Element;
import javassist.util.proxy.Proxy;
import javassist.util.proxy.ProxyFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.gremlin.repository.GremlinGraphAdapter;
import org.springframework.data.gremlin.repository.GremlinRepository;
import org.springframework.data.gremlin.schema.property.GremlinAdjacentProperty;
import org.springframework.data.gremlin.schema.property.GremlinProperty;
import org.springframework.data.gremlin.schema.property.accessor.GremlinFieldPropertyAccessor;
import org.springframework.data.gremlin.schema.property.accessor.GremlinIdPropertyAccessor;
import org.springframework.data.gremlin.schema.property.accessor.GremlinPropertyAccessor;
import org.springframework.data.gremlin.schema.property.encoder.GremlinPropertyEncoder;
import org.springframework.data.gremlin.schema.property.mapper.GremlinPropertyMapper;
import org.springframework.data.gremlin.tx.GremlinGraphFactory;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Modifier;
import java.util.*;
import java.util.stream.Stream;

/**
 * <p>
 * Defines the schema of a mapped Class. Each GremlinSchema holds the {@code className}, {@code classType},
 * {@code schemaType} (VERTEX, EDGE) and the identifying {@link GremlinFieldPropertyAccessor}.
 * </p>
 * <p>
 * The GremlinSchema contains the high level logic for converting Vertices to mapped classes.
 * </p>
 *
 * @author Gman
 */
public abstract class GremlinSchema<V> {

    private static final Logger LOGGER = LoggerFactory.getLogger(GremlinSchema.class);

    public GremlinSchema(Class<V> classType, GremlinSchema<? super V> superSchema) {
        this.classType = classType;
        this.superSchema = superSchema;
        this.isAbstract = Modifier.isAbstract(classType.getModifiers());
    }

    private final Class<V> classType;
    private final GremlinSchema<? super V> superSchema;
    private Map<String, GremlinSchema<? extends V>> inheritedClassSchemaMapping;
    private boolean isAbstract;

    private String className;
    private GremlinRepository<V> repository;
    private GremlinGraphFactory graphFactory;
    private GremlinIdPropertyAccessor idAccessor;
    private GremlinPropertyMapper idMapper;
    private GremlinPropertyEncoder idEncoder;

    private GremlinAdjacentProperty outProperty;
    private GremlinAdjacentProperty inProperty;

    private Map<String, GremlinProperty> propertyMap = new HashMap<>();
    private Map<String, GremlinProperty> fieldToPropertyMap = new HashMap<>();
    private Multimap<Class<?>, GremlinProperty> typePropertyMap = LinkedListMultimap.create();

    private Set<GremlinProperty> properties = new HashSet<>();

    private Class<? extends V> proxyClass;

    public void addProperty(GremlinProperty property) {
        property.setSchema(this);
        if (property instanceof GremlinAdjacentProperty) {
            if (((GremlinAdjacentProperty) property).getDirection() == Direction.OUT) {
                outProperty = (GremlinAdjacentProperty) property;
            } else {
                inProperty = (GremlinAdjacentProperty) property;
            }
        }
        properties.add(property);
        propertyMap.put(property.getName(), property);
        if (property.getAccessor() instanceof GremlinFieldPropertyAccessor) {
            fieldToPropertyMap.put(((GremlinFieldPropertyAccessor) property.getAccessor()).getField().getName(), property);
        }
        typePropertyMap.put(property.getType(), property);
    }

    public GremlinProperty getPropertyForFieldname(String fieldname) {
        return fieldToPropertyMap.get(fieldname);
    }

    public GremlinPropertyMapper getIdMapper() {
        return idMapper;
    }

    public void setIdMapper(GremlinPropertyMapper idMapper) {
        this.idMapper = idMapper;
    }

    public GremlinGraphFactory getGraphFactory() {
        return graphFactory;
    }

    public void setGraphFactory(GremlinGraphFactory graphFactory) {
        this.graphFactory = graphFactory;
    }

    public GremlinRepository<V> getRepository() {
        return repository;
    }

    public void setRepository(GremlinRepository<V> repository) {
        this.repository = repository;
    }

    public GremlinPropertyEncoder getIdEncoder() {
        return idEncoder;
    }

    public void setIdEncoder(GremlinPropertyEncoder idEncoder) {
        this.idEncoder = idEncoder;
    }

    public GremlinIdPropertyAccessor getIdAccessor() {
        if (idAccessor == null && superSchema != null) {
            return superSchema.getIdAccessor();
        } else return idAccessor;
    }

    public void setIdAccessor(GremlinIdPropertyAccessor idAccessor) {
        this.idAccessor = idAccessor;
    }

    public Collection<String> getPropertyNames() {
        return propertyMap.keySet();
    }

    public GremlinPropertyAccessor getAccessor(String property) {
        return propertyMap.get(property).getAccessor();
    }

    public Stream<GremlinProperty> getPropertyStream() {
        if (superSchema != null) {
            return Stream.concat(superSchema.getPropertyStream(), properties.stream());
        }
        return properties.stream();
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public Class<V> getClassType() {
        return classType;
    }

    public GremlinProperty getProperty(String property) {
        return propertyMap.get(property);
    }

    public Collection<GremlinProperty> getPropertyForType(Class<?> type) {
        return typePropertyMap.get(type);
    }

    public boolean isVertexSchema() {
        return this instanceof GremlinVertexSchema;
    }

    public boolean isEdgeSchema() {
        return this instanceof GremlinEdgeSchema;
    }

    public GremlinAdjacentProperty getOutProperty() {
        return outProperty;
    }

    public GremlinAdjacentProperty getInProperty() {
        return inProperty;
    }

    public void copyToGraph(GremlinGraphAdapter graphAdapter, Element element, Object obj, Object... noCascade) {
        Map<Object, Element> noCascadingMap = new HashMap<>();
        for (Object skip : noCascade) {
            noCascadingMap.put(skip, element);
        }
        cascadeCopyToGraph(graphAdapter, element, obj, noCascadingMap);
    }

    public void copyToGraph(GremlinGraphAdapter graphAdapter, Element element, Object obj) {
        cascadeCopyToGraph(graphAdapter, element, obj, new HashMap<>());
    }

    public void cascadeCopyToGraph(GremlinGraphAdapter graphAdapter, Element element, final Object obj, Map<Object, Element> noCascadingMap) {

        if (noCascadingMap.containsKey(obj)) {
            return;
        }
        noCascadingMap.put(obj, element);

        getPropertyStream().forEach(property -> {
            try {

                GremlinPropertyAccessor accessor = property.getAccessor();
                Object val = accessor.get(obj);

                if (val != null) {
                    property.copyToVertex(graphAdapter, element, val, noCascadingMap);
                }
            } catch (RuntimeException e) {
                LOGGER.warn(String.format("Could not save property %s of %s", property, obj.toString()), e);
            }
        });

        if (getGraphId(obj) == null && TransactionSynchronizationManager.isSynchronizationActive()) {
            final Element finalElement = element;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
                @Override
                public void afterCommit() {
                    setObjectId(obj, finalElement);
                }
            });
        }
    }

    public V loadFromGraph(GremlinGraphAdapter graphAdapter, Element element) {

        return cascadeLoadFromGraph(graphAdapter, element, new HashMap<>());
    }

    private void initProxy() {
        if (proxyClass != null) {
            return;
        }
        ProxyFactory factory = new ProxyFactory();
        factory.setSuperclass(getClassType());
        //noinspection unchecked
        proxyClass = factory.createClass();
    }

    public V cascadeLoadFromGraph(final GremlinGraphAdapter graphAdapter, final Element element, final Map<Object, Object> noCascadingMap) {
        //noinspection unchecked
        V obj = (V) noCascadingMap.get(element.getId());
        if (obj != null) {
            return obj;
        }
        String className = graphAdapter.getClassName(element);
        GremlinSchema<? extends V> schema = findMostSpecificSchema(className);
        return schema.specificCascadeLoadFromGraph(graphAdapter, element, noCascadingMap);
    }

    private V specificCascadeLoadFromGraph(GremlinGraphAdapter graphAdapter, Element element, Map<Object, Object> noCascadingMap) {
        V obj;
        initProxy();
        try {
            obj = proxyClass.newInstance();
            noCascadingMap.put(element.getId(), obj);
            ((Proxy) obj).setHandler(new LazyInitializationHandler(this, graphAdapter, element, noCascadingMap));
            setObjectId(obj, element);
            return obj;
        } catch (InstantiationException | IllegalAccessException e) {
            throw new IllegalStateException("Could not instantiate new " + getClassType(), e);
        }
    }

    public String getGraphId(Object obj) {
        return decodeId(getIdAccessor().get(obj));
    }

    public void setObjectId(Object obj, Element element) {
        getIdAccessor().set(obj, encodeId(element.getId().toString()));
    }

    public String getObjectId(Object obj) {
        String id = getIdAccessor().get(obj);
        if (id != null) {
            return decodeId(id);
        }
        return null;
    }

    public String encodeId(String id) {
        if (id == null) {
            return null;
        }
        if (idEncoder != null) {
            id = idEncoder.encode(id).toString();
        }
        return id;
    }

    public String decodeId(String id) {
        if (id == null) {
            return null;
        }
        if (idEncoder != null) {
            id = idEncoder.decode(id).toString();
        }
        return id;
    }

    public GremlinSchema<?> getSuperSchema() {
        return superSchema;
    }

    public boolean isAbstract() {
        return isAbstract;
    }

    public void setAbstract(boolean isAbstract) {
        this.isAbstract = isAbstract;
    }

    public void addInheritedSchema(GremlinSchema<? extends V> gremlinSchema) {
        if (inheritedClassSchemaMapping == null) {
            inheritedClassSchemaMapping = new HashMap<>();
        }
        inheritedClassSchemaMapping.put(gremlinSchema.getClassName(), gremlinSchema);
    }

    @Override
    public String toString() {
        return "GremlinSchema{"
            + "className='" + className + '\''
            + ", classType=" + classType +
            '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        GremlinSchema<?> that = (GremlinSchema<?>) o;

        return classType.equals(that.classType);
    }

    @Override
    public int hashCode() {
        return classType.hashCode();
    }

    public GremlinSchema<? extends V> findMostSpecificSchema(Class<?> aClass) {
        if (inheritedClassSchemaMapping == null) {
            return this;
        }
        for (GremlinSchema<? extends V> schema : inheritedClassSchemaMapping.values()) {
            if (schema.getClassType().isAssignableFrom(aClass)) {
                return schema.findMostSpecificSchema(aClass);
            }
        }
        return this;
    }

    public GremlinSchema<? extends V> findMostSpecificSchema(String className) {
        if (inheritedClassSchemaMapping == null || className == null) {
            return this;
        }
        return inheritedClassSchemaMapping.getOrDefault(className, this);
    }
}
