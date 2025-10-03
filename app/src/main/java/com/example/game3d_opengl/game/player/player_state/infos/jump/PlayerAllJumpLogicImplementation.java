package com.example.game3d_opengl.game.player.player_state.infos.jump;

import java.util.Objects;

/**
 * Uses an automaton / finite state machine to determine if the player can jump or not
 */
public class PlayerAllJumpLogicImplementation implements PlayerAllJumpVisitor {


    // We feed the information to the automaton in fixed order, in order to reduce number of states.
    // When information instance arrives, we cache it instead of processing right away.
    // When asked for result, we order the info by their "application order" and feed to automaton.
    
    // Application order constants (from lowest to highest priority)
    private static final int ORDER_PLAYER_WANTS_JUMP = 0;
    private static final int ORDER_PLAYER_HAS_FOOTING = 1;
    private static final int ORDER_PLAYER_HAS_JUMP_CHARGES = 2;
    private static final int ORDER_PLAYER_HITS_GROUND_SOON = 3;
    private static final int ORDER_PLAYER_HITS_SPIKE_SOON = 4;
    
    // Maximum capacity for jump info array
    private static final int MAX_JUMP_INFOS = 16;
    
    // Pair of info and its explicit application order
    private static final class InfoAndOrder {
        PlayerJumpInfo info;
        int order;
        InfoAndOrder(PlayerJumpInfo info, int order) {
            this.info = info;
            this.order = order;
        }
    }

    // Pre-allocated array for info+order pairs
    private final InfoAndOrder[] infos = new InfoAndOrder[MAX_JUMP_INFOS];
    private int infoCount = 0;
    
    // Cached result
    private boolean cachedShouldJump = false;
    private boolean resultCalculated = false;
    
    // Inner automaton class
    private final AllJumpAutomaton automaton = new AllJumpAutomaton();

    // ------------------------------------- PUBLIC API --------------------------------------------

    public PlayerAllJumpLogicImplementation(){
        for(int i=0;i<infos.length;++i){
            infos[i] = new InfoAndOrder(null, Integer.MAX_VALUE);
        }
    }
    
    /**
     * Clean up the automaton state at the beginning of each frame.
     */
    public void resetFrame() {
        infoCount = 0;
        resultCalculated = false;
        automaton.reset();
    }
    
    /**
     *
     */
    public boolean shouldJump() {
        // results are cached (within a frame) if this is called multiple times within a frame.
        if (!resultCalculated) {
            // Sort the collected jump infos by application order
            sortInfosByOrder();
            
            // Process each jump info through the automaton
            for (int i = 0; i < infoCount; i++) {
                infos[i].info.accept(automaton);
            }
            
            // Get final result from automaton
            cachedShouldJump = automaton.shouldJump();
            resultCalculated = true;
        }
        return cachedShouldJump;
    }

    // Visitor methods - store info in pre-allocated array
    @Override
    public void visit(PlayerJumpInfo.PlayerHasFooting info) {
        addInfo(info, ORDER_PLAYER_HAS_FOOTING);
    }

    @Override
    public void visit(PlayerJumpInfo.PlayerWantsJump info) {
        addInfo(info, ORDER_PLAYER_WANTS_JUMP);
    }

    @Override
    public void visit(PlayerJumpInfo.PlayerHitsGroundSoon info) {
        addInfo(info, ORDER_PLAYER_HITS_GROUND_SOON);
    }

    @Override
    public void visit(PlayerJumpInfo.PlayerHitsSpikeSoon info) {
        addInfo(info, ORDER_PLAYER_HITS_SPIKE_SOON);
    }

    @Override
    public void visit(PlayerJumpInfo.PlayerHasJumpCharges info) {
        addInfo(info, ORDER_PLAYER_HAS_JUMP_CHARGES);
    }


    // ------------------------------------ END PUBLIC API -----------------------------------------
    
    /**
     * Sort jump infos by application order
     */
    private void sortInfosByOrder() {
        // Simple insertion sort for the used portion of the array
        for (int i = 1; i < infoCount; i++) {
            InfoAndOrder key = infos[i];
            int j = i - 1;
            while (j >= 0 && infos[j].order > key.order) {
                infos[j + 1] = infos[j];
                j--;
            }
            infos[j + 1] = key;
        }
    }
    
    /**
     * Add jump info with explicit order to the pre-allocated array
     */
    private void addInfo(PlayerJumpInfo info, int order) {
        if (infoCount < MAX_JUMP_INFOS) {
            infos[infoCount].info = info;
            infos[infoCount++].order = order;
        }
        // If array is full, we ignore new infos to keep it simple
    }
    
    /**
     * Inner automaton class that implements PlayerJumpVisitor
     */
    private static class AllJumpAutomaton implements PlayerAllJumpVisitor {
        
        // Automaton states
        private enum State {
            BASE_STATE,
            WANT,
            YES_1,
            MAYBE_AIR_JUMP,
            MAYBE_NOT,
            YES_2,
            ERROR
        }
        
        private State currentState = State.BASE_STATE;
        
        public void reset() {
            currentState = State.BASE_STATE;
        }
        
        public boolean shouldJump() {
            return currentState == State.YES_1 || currentState == State.YES_2;
        }
        
        @Override
        public void visit(PlayerJumpInfo.PlayerWantsJump info) {
            if (Objects.requireNonNull(currentState) == State.BASE_STATE) {
                currentState = State.WANT;
            } else {
                currentState = State.ERROR;
            }
        }
        
        @Override
        public void visit(PlayerJumpInfo.PlayerHasFooting info) {
            if (Objects.requireNonNull(currentState) == State.WANT) {
                currentState = State.YES_1;
            } else {
                currentState = State.ERROR;
            }
        }
        
        @Override
        public void visit(PlayerJumpInfo.PlayerHasJumpCharges info) {
            if (Objects.requireNonNull(currentState) == State.WANT) {
                currentState = State.MAYBE_AIR_JUMP;
            } else {
                currentState = State.ERROR;
            }
        }
        
        @Override
        public void visit(PlayerJumpInfo.PlayerHitsGroundSoon info) {
            if (Objects.requireNonNull(currentState) == State.MAYBE_AIR_JUMP) {
                currentState = State.MAYBE_NOT;
            } else {
                currentState = State.ERROR;
            }
        }
        
        @Override
        public void visit(PlayerJumpInfo.PlayerHitsSpikeSoon info) {
            if (Objects.requireNonNull(currentState) == State.MAYBE_NOT) {
                currentState = State.YES_2;
            } else {
                currentState = State.ERROR;
            }
        }
    }
}
