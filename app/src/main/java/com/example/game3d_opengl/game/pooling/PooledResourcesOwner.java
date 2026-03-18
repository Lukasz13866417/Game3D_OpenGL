package com.example.game3d_opengl.game.pooling;

import java.util.ArrayList;

public abstract class PooledResourcesOwner implements PooledLease {

    protected PooledResourcesOwner(BaseBuilder<?, ?> builder) {
    }


    /**
     * Release owned pooled resources for
     * 1. this instance, and
     * 2. (recursively) for all PooledResourceOwners owned by this instance.
     */
    public abstract void releasePooledResourcesRecursively();

    @Override
    public final void release() {
        releasePooledResourcesRecursively();
    }

    public static abstract class BaseBuilder<T, B extends BaseBuilder<T, B>> {

        private final ArrayList<PooledLease> leases;
        // Children are pooled leases owned by this builder/product.
        // They can be PooledResourcesOwner instances or leaser wrappers.
        private final ArrayList<PooledLease> childLeases;

        public BaseBuilder() {
            this.leases = new ArrayList<>();
            this.childLeases = new ArrayList<>();
        }

        protected final void rollback() {
            for (int i = childLeases.size() - 1; i >= 0; --i) {
                childLeases.get(i).release();
            }
            childLeases.clear();
            for (int i = leases.size() - 1; i >= 0; --i) {
                leases.get(i).release();
            }
            leases.clear();
        }

        protected final <L extends PooledLease> L take(L lease) {
            leases.add(lease);
            return lease;
        }

        protected final <L extends PooledLease> L addChild(L child) {
            childLeases.add(child);
            return child;
        }

        protected abstract B self();

        /** 
            Subclasses can store leases as fields of specific types. 
            Then, in createWhenReady, these fields can be passed to the object built
        **/
        public abstract T createWhenReady();

        public final T build() {
            try {
                T out = createWhenReady();
                childLeases.clear(); // ownership transferred
                leases.clear(); // ownership transferred
                return out;
            } catch (Throwable t) {
                rollback();
                throw t;
            }
        }
    }

}
