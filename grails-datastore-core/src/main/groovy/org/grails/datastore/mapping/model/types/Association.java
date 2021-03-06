/* Copyright 2004-2005 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.datastore.mapping.model.types;

import java.beans.PropertyDescriptor;
import java.util.*;

import javax.persistence.CascadeType;
import javax.persistence.FetchType;

import org.grails.datastore.mapping.config.Property;
import org.grails.datastore.mapping.model.*;

/**
 * Models an association between one class and another
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@SuppressWarnings("rawtypes")
public abstract class Association<T extends Property> extends AbstractPersistentProperty<T> {

    private static final Set<CascadeType> DEFAULT_OWNER_CASCADE = Collections.unmodifiableSet(new HashSet<>(Collections.singletonList(CascadeType.ALL)));

    private static final Set<CascadeType> DEFAULT_CHILD_CASCADE = Collections.unmodifiableSet(new HashSet<>(Collections.singletonList(CascadeType.PERSIST)));

    private PersistentEntity associatedEntity;
    private String referencedPropertyName;
    private boolean owningSide;
    private Set<CascadeType> cascadeOperations;

    private static final Map<String, CascadeType> cascadeTypeConversions = new LinkedHashMap<>();

    static {
        cascadeTypeConversions.put("all", CascadeType.ALL);
        cascadeTypeConversions.put("merge", CascadeType.MERGE);
        cascadeTypeConversions.put("save-update", CascadeType.PERSIST);
        cascadeTypeConversions.put("delete", CascadeType.REMOVE);
        cascadeTypeConversions.put("remove", CascadeType.REMOVE);
        cascadeTypeConversions.put("refresh", CascadeType.REFRESH);
        cascadeTypeConversions.put("persist", CascadeType.PERSIST);
        // Unsupported Types
        // "all-delete-orphan", "lock", "replicate", "evict", "delete-orphan"
    }

    public Association(PersistentEntity owner, MappingContext context, PropertyDescriptor descriptor) {
        super(owner, context, descriptor);
    }

    public Association(PersistentEntity owner, MappingContext context, String name, Class type) {
        super(owner, context, name, type);
    }

    private void buildCascadeOperations() {
        final String cascade = this.getMapping().getMappedForm().getCascade();
        if (cascade != null) {
            final String[] specifiedOperations = cascade.toLowerCase().split(",");
            cascadeOperations = new HashSet<>();
            for(final String operation: specifiedOperations) {
                final String key = operation.trim();
                if (cascadeTypeConversions.containsKey(key)) {
                    cascadeOperations.add(cascadeTypeConversions.get(key));
                }
            }
            cascadeOperations = Collections.unmodifiableSet(cascadeOperations);
        } else {
            if (isOwningSide()) {
                cascadeOperations = DEFAULT_OWNER_CASCADE;
            }
            else {
                if((this instanceof ManyToOne) && isBidirectional()) {
                    // don't cascade by default to many-to-one that is not owned
                    cascadeOperations = Collections.<CascadeType>emptySet();
                }
                else {
                    cascadeOperations = DEFAULT_CHILD_CASCADE;
                }
            }
        }
    }

    /**
     * @return The fetch strategy for the association
     */
    public FetchType getFetchStrategy() {
        return getMapping().getMappedForm().getFetchStrategy();
    }

    public boolean isBidirectional() {
        return associatedEntity != null && referencedPropertyName != null;
    }

    /**
     * @return The inverside side or null if the association is not bidirectional
     */
    public Association getInverseSide() {
        final PersistentProperty associatedProperty = associatedEntity.getPropertyByName(referencedPropertyName);
        if (associatedProperty == null) return null;
        if (associatedProperty instanceof Association) {
            return (Association) associatedProperty;
        }
        throw new IllegalMappingException("The inverse side [" + associatedEntity.getName() + "." +
                associatedProperty.getName() + "] of the association [" + getOwner().getName() + "." +
                getName() + "] is not valid. Associations can only map to other entities and collection types.");
    }

    /**
     * Returns true if the this association cascade for the given cascade operation
     *
     * @param cascadeOperation The cascadeOperation
     * @return True if it does
     */
    public boolean doesCascade(CascadeType cascadeOperation) {
        Set<CascadeType> cascades = getCascadeOperations();
        return cascadeOperation != null && (cascades.contains(CascadeType.ALL) || cascades.contains(cascadeOperation));
    }

    /**
     * Returns true if the this association cascade for the given cascade operation
     *
     * @param cascadeOperations The cascadeOperations
     * @return True if it does
     */
    public boolean doesCascade(CascadeType... cascadeOperations) {
        Set<CascadeType> cascades = getCascadeOperations();
        if( cascades.contains(CascadeType.ALL) ) {
            return true;
        }
        else if(cascadeOperations != null) {
            for (CascadeType cascadeOperation : cascadeOperations) {
                if(cascades.contains(cascadeOperation)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * @return Whether this association is embedded
     */
    public boolean isEmbedded() {
        return this instanceof Embedded || this instanceof EmbeddedCollection;
    }

    /**
     * @return Whether this association is embedded
     */
    public boolean isBasic() {
        return this instanceof Basic;
    }

    protected Set<CascadeType> getCascadeOperations() {
        if (cascadeOperations == null) {
            buildCascadeOperations();
        }
        return cascadeOperations;
    }

    /**
     * Returns whether this side owns the relationship. This controls
     * the default cascading behavior if none is specified
     *
     * @return True if this property is the owning side
     */
    public boolean isOwningSide() {
        return owningSide;
    }

    public void setOwningSide(boolean owningSide) {
        this.owningSide = owningSide;
    }

    public void setAssociatedEntity(PersistentEntity associatedEntity) {
        this.associatedEntity = associatedEntity;
    }

    public PersistentEntity getAssociatedEntity() {
        return associatedEntity;
    }

    public void setReferencedPropertyName(String referencedPropertyName) {
        this.referencedPropertyName = referencedPropertyName;
    }

    public String getReferencedPropertyName() {
        return referencedPropertyName;
    }

    @Override
    public String toString() {
        return getOwner().getName() + "->" + getName();
    }

    /**
     * @return Whether the association is a List
     */
    public boolean isList() {
        return List.class.isAssignableFrom(getType());
    }

    /**
     * @return Whether the association is circular
     */
    public boolean isCircular() {
        PersistentEntity associatedEntity = getAssociatedEntity();
        if(associatedEntity == null) {
            return false;
        }
        else {
            return associatedEntity.getJavaClass().isAssignableFrom(owner.getJavaClass());
        }
    }


}
