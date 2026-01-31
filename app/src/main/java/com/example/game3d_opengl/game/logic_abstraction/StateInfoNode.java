package com.example.game3d_opengl.game.logic_abstraction;

import java.util.ArrayList;
import java.util.List;

public abstract class StateInfoNode<DataClass> {

    int nUsers; // only for bookkeeping in StateInfoGraph
    int nReadyUsers; // only for bookkeeping in StateInfoGraph
    int indInOriginalOrdering; // only for bookkeeping in StateInfoGraph

    public abstract void setData(DataClass what);
    public abstract DataClass getData();

    public abstract void calc();


    public abstract static class BaseBuilder<D, T extends StateInfoNode<D>, B extends BaseBuilder<D,T,B>>{
        private final List<StateInfoNode<D>> dependencies;

        public BaseBuilder(){
            this.dependencies = new ArrayList<>();
        }

        public B addDependency(StateInfoNode<D> what){
            dependencies.add(what);
            what.nUsers++;
            return self();
        }
        protected List<StateInfoNode<D>> getDependencies(){
            return dependencies;
        }
        protected abstract B self();
        protected abstract boolean isReadyChild();
        protected abstract T create();
        private boolean isReadyBase(){
            return true;
        }
        public final T addToGraphAndBuild(StateInfoGraph<?,?>.GraphSetupAPI setupAPI){
            assert isReadyBase();
            assert isReadyChild();
            T res = create();
            setupAPI.addNode(res, dependencies);
            return res;
        }
    }
}
