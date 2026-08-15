package com.example.game3d.authoring;

import com.example.game3d.core.terrain.SurfaceProperties;
import com.example.game3d.core.terrain.addon.Portal;

import java.util.ArrayList;
import java.util.List;

/** Parity-locked structure definitions for the production intro and six templates. */
public final class GameplayTerrainFactory {
    private GameplayTerrainFactory() {}

    public static TerrainLevelSequence intro() {
        return new TerrainLevelSequence("builtin:intro",
                straight("intro-safe", 80, SurfaceProperties.NORMAL),
                new CanonicalSection("intro-hazards", 80) {
                    @Override void tiles(Terrain.TileBrush brush) { addStraight(brush, 80, SurfaceProperties.NORMAL, 1f); }
                    @Override void addons(Terrain.AdvancedGridBrush brush) {
                        spike(brush, 20, 80); spike(brush, 40, 80); spike(brush, 60, 80);
                    }
                });
    }

    public static TerrainLevelSequence gameplayLevel(int levelOrdinal) {
        int safeIndex = Math.max(0, levelOrdinal);
        long mixed = mix64(safeIndex + 0x9e3779b97f4a7c15L);
        int template = (int) Math.floorMod(mixed, 6L);
        return gameplayTemplate(template, safeIndex, mixed);
    }

    /** Builds one fixed catalog template while retaining ordinal-derived portal policy. */
    static TerrainLevelSequence gameplayTemplate(int templateIndex, int levelOrdinal) {
        int safeIndex = Math.max(0, levelOrdinal);
        long mixed = mix64(safeIndex + 0x9e3779b97f4a7c15L);
        return gameplayTemplate(Math.floorMod(templateIndex, 6), safeIndex, mixed);
    }

    private static TerrainLevelSequence gameplayTemplate(
            int template, int safeIndex, long mixed) {
        boolean portal = safeIndex >= 4 && ((mixed >>> 8) & 3L) == 0L;
        boolean portalFirst = ((mixed >>> 12) & 1L) == 0L;
        String prefix = "builtin:level-" + safeIndex + ":";
        List<BaseTerrainStructure<?>> parts = new ArrayList<BaseTerrainStructure<?>>();
        switch (template) {
            case 0:
                parts.add(straight(prefix+"straight-a",20,SurfaceProperties.NORMAL));
                parts.add(stairs(prefix+"stairs",30,5,2,Math.PI/8.0,-0.9));
                parts.add(straight(prefix+"straight-b",12,SurfaceProperties.NORMAL));
                parts.add(curve(prefix+"curve-a",12,-Math.PI/8.0,0.0,5));
                parts.add(obstacle(prefix+"obstacle-a",34,portal&&portalFirst));
                parts.add(curve(prefix+"curve-b",42,0.0,Math.PI/14.0,5));
                parts.add(straight(prefix+"straight-c",16,SurfaceProperties.NORMAL));
                parts.add(obstacle(prefix+"obstacle-b",40,portal&&!portalFirst));
                parts.add(straight(prefix+"straight-d",34,SurfaceProperties.NORMAL));
                break;
            case 1:
                parts.add(straight(prefix+"straight-a",50,SurfaceProperties.NORMAL));
                parts.add(stairs(prefix+"stairs",42,7,2,Math.PI/9.0,-0.8));
                parts.add(straight(prefix+"straight-b",12,SurfaceProperties.NORMAL));
                parts.add(curve(prefix+"curve",14,-Math.PI/9.0,0.0,5));
                parts.add(straight(prefix+"straight-c",50,SurfaceProperties.NORMAL));
                break;
            case 2:
                parts.add(straight(prefix+"straight-a",24,SurfaceProperties.NORMAL));
                parts.add(obstacle(prefix+"obstacle",28,portal));
                parts.add(boost(prefix+"boost",8,0,40,Math.PI/15.0));
                parts.add(straight(prefix+"straight-b",40,SurfaceProperties.NORMAL));
                break;
            case 3:
                parts.add(straight(prefix+"straight-a",20,SurfaceProperties.NORMAL));
                parts.add(curve(prefix+"curve",18,Math.PI/16.0,0.0,5));
                parts.add(straight(prefix+"straight-b",30,SurfaceProperties.NORMAL));
                parts.add(boost(prefix+"boost",7,0,18,Math.PI/7.0));
                parts.add(straight(prefix+"straight-c",22,SurfaceProperties.NORMAL));
                parts.add(obstacle(prefix+"obstacle",30,portal));
                parts.add(straight(prefix+"straight-d",30,SurfaceProperties.NORMAL));
                break;
            case 4:
                parts.add(straight(prefix+"straight-a",50,SurfaceProperties.NORMAL));
                parts.add(stairs(prefix+"stairs",35,5,2,Math.PI/10.0,-0.85));
                parts.add(straight(prefix+"straight-b",20,SurfaceProperties.NORMAL));
                parts.add(curve(prefix+"curve-a",12,-Math.PI/10.0,0.0,5));
                parts.add(obstacle(prefix+"obstacle",50,portal));
                parts.add(curve(prefix+"curve-b",30,Math.PI/20.0,Math.PI/22.0,5));
                parts.add(straight(prefix+"straight-c",18,SurfaceProperties.NORMAL));
                break;
            default:
                parts.add(straight(prefix+"straight-a",30,SurfaceProperties.NORMAL));
                parts.add(obstacle(prefix+"obstacle-a",42,portal&&portalFirst));
                parts.add(curve(prefix+"curve-a",36,0.0,Math.PI/16.0,5));
                parts.add(straight(prefix+"straight-b",18,SurfaceProperties.NORMAL));
                parts.add(obstacle(prefix+"obstacle-b",32,portal&&!portalFirst));
                parts.add(stairs(prefix+"stairs",18,4,2,Math.PI/7.0,-1.0));
                parts.add(straight(prefix+"straight-c",12,SurfaceProperties.NORMAL));
                parts.add(curve(prefix+"curve-b",20,-Math.PI/7.0,0.0,5));
                parts.add(straight(prefix+"straight-d",34,SurfaceProperties.NORMAL));
                break;
        }
        return new TerrainLevelSequence("builtin:level-" + safeIndex,
                parts.toArray(new BaseTerrainStructure<?>[parts.size()]));
    }

    public static int templateForLevel(int levelOrdinal) {
        int safeIndex = Math.max(0, levelOrdinal);
        return (int) Math.floorMod(
                mix64(safeIndex + 0x9e3779b97f4a7c15L), 6L);
    }

    private static CanonicalSection straight(
            final String id, final int count, final SurfaceProperties surface) {
        return new CanonicalSection(id,count) {
            @Override void tiles(Terrain.TileBrush brush) { addStraight(brush,count,surface,1f); }
            @Override void addons(Terrain.AdvancedGridBrush brush) {}
        };
    }

    private static CanonicalSection obstacle(final String id, final int count, final boolean portal) {
        return new CanonicalSection(id,count) {
            @Override void tiles(Terrain.TileBrush brush) { addStraight(brush,count,SurfaceProperties.NORMAL,1f); }
            @Override void addons(Terrain.AdvancedGridBrush brush) {
                potion(brush,count/4); potion(brush,count/2); potion(brush,(count*3)/4);
                spike(brush,count/3,count); spike(brush,(count*2)/3,count);
                if (portal) {
                    String pair=id+":portal-pair";
                    // Preserve the historical exit-first declaration/ID ordering.
                    portal(brush,3,pair, Portal.Role.EXIT);
                    portal(brush,count-4,pair, Portal.Role.ENTRANCE);
                }
            }
        };
    }

    private static CanonicalSection curve(final String id, final int curveSegments,
            final double yawDelta, final double pitchDelta, final int fadeSegments) {
        return new CanonicalSection(id,curveSegments+ (Math.abs(pitchDelta)>1.0e-12?fadeSegments:0)) {
            @Override void tiles(Terrain.TileBrush brush) {
                double yawStep=yawDelta/curveSegments, pitchStep=pitchDelta/curveSegments;
                for(int i=0;i<curveSegments;i++) {
                    brush.addHorizontalAng(yawStep); brush.addVerticalAng(pitchStep);
                    addOne(brush,SurfaceProperties.NORMAL,1f,1f);
                }
                if(Math.abs(pitchDelta)>1.0e-12) {
                    if(fadeSegments==0) brush.addVerticalAng(-pitchDelta);
                    else for(int i=0;i<fadeSegments;i++) {
                        brush.addVerticalAng(-pitchDelta/fadeSegments);
                        addOne(brush,SurfaceProperties.NORMAL,1f,1f);
                    }
                }
            }
            @Override void addons(Terrain.AdvancedGridBrush brush) {
                spike(brush,curveSegments/3,curveSegments);
                spike(brush,(curveSegments*2)/3,curveSegments);
            }
        };
    }

    private static CanonicalSection stairs(final String id, final int perStair,
            final int stairCount, final int gapSegments,
            final double yawDelta, final double stepHeight) {
        final int rows=perStair*stairCount+gapSegments*Math.max(0,stairCount-1);
        return new CanonicalSection(id,rows) {
            final List<Integer> spikeRows=new ArrayList<Integer>();
            final List<Integer> spikeSalts=new ArrayList<Integer>();
            @Override void tiles(Terrain.TileBrush brush) {
                double yawStep=yawDelta/(perStair*stairCount);
                brush.liftUp(stepHeight);
                int row=0;
                for(int stair=0;stair<stairCount;stair++) {
                    for(int i=0;i<perStair;i++) {
                        brush.addHorizontalAng(yawStep);
                        addOne(brush,SurfaceProperties.NORMAL,0.5f,1f);
                        if(i==perStair/2) { spikeRows.add(row); spikeSalts.add(stair+i); }
                        row++;
                    }
                    if(stair==stairCount-1) { brush.liftUp(stepHeight); continue; }
                    for(int gap=0;gap<gapSegments;gap++) {
                        brush.setUpcomingSurface(SurfaceProperties.NORMAL);
                        brush.setCornerAlphas(0.5f,0.5f);
                        brush.setUpcomingBrightnessMultiplier(1f);
                        brush.addEmptySegment(); row++;
                    }
                    brush.liftUp(stepHeight);
                }
            }
            @Override void addons(Terrain.AdvancedGridBrush brush) {
                for(int i=0;i<spikeRows.size();i++) spike(brush,spikeRows.get(i),
                        perStair*stairCount,spikeSalts.get(i));
            }
        };
    }

    private static CanonicalSection boost(final String id, final int ramp,
            final int gaps, final int landing, final double launchPitch) {
        return new CanonicalSection(id,ramp+gaps+landing) {
            @Override void tiles(Terrain.TileBrush brush) {
                double step=launchPitch/ramp;
                for(int i=0;i<ramp;i++) {
                    brush.addVerticalAng(step);
                    SurfaceProperties surface=i==ramp-1
                            ?SurfaceProperties.BOOST_RAMP_LAUNCH:SurfaceProperties.BOOST_RAMP;
                    float t=ramp<=1?1f:(float)i/(ramp-1);
                    addOne(brush,surface,1f,1f+0.35f*t);
                }
                brush.setVerticalAng(0.0);
                for(int i=0;i<gaps;i++) {
                    brush.setUpcomingSurface(SurfaceProperties.NORMAL);
                    brush.setCornerAlphas(1f,1f); brush.setUpcomingBrightnessMultiplier(1f);
                    brush.addEmptySegment();
                }
                addStraight(brush,landing,SurfaceProperties.NORMAL,1f);
            }
            @Override void addons(Terrain.AdvancedGridBrush brush) {}
        };
    }

    private abstract static class CanonicalSection extends AdvancedTerrainStructure {
        final String id;
        CanonicalSection(String id,int rows){super(rows);this.id=id;this.name=id;}
        @Override protected final void generateTiles(Terrain.TileBrush brush){tiles(brush);}
        @Override protected final void generateAddons(Terrain.AdvancedGridBrush brush,int rows,int columns){addons(brush);}
        abstract void tiles(Terrain.TileBrush brush);
        abstract void addons(Terrain.AdvancedGridBrush brush);
        final void addStraight(Terrain.TileBrush b,int count,SurfaceProperties s,float alpha){
            for(int i=0;i<count;i++) addOne(b,s,alpha,s.kind==SurfaceProperties.Kind.NORMAL?1f:1.25f);
        }
        final void addOne(Terrain.TileBrush b,SurfaceProperties s,float alpha,float brightness){
            b.setUpcomingSurface(s);b.setCornerAlphas(alpha,alpha);
            b.setUpcomingBrightnessMultiplier(brightness);b.addSegment();
        }
        final void spike(Terrain.AdvancedGridBrush b,int index,int count){spike(b,index,count,index);}
        final void spike(Terrain.AdvancedGridBrush b,int index,int count,int salt){
            double radius=Math.min(TrackProfile.gameplayDefault().width*0.10,
                    TrackProfile.gameplayDefault().tileLength*0.25);
            double sign=((salt+count)&1)==0?-1.0:1.0;
            b.placePoseAlignedOnSegment(index+1,sign*0.27,radius,radius,
                    AddonBlueprint.canonicalDeathSpike(id+":spike:"+index,salt,radius));
        }
        final void potion(Terrain.AdvancedGridBrush b,int index){
            b.placePoseAlignedOnSegment(index+1,0.0,0.05,0.05,
                    AddonBlueprint.airJumpPotion(id+":potion:"+index));
        }
        final void portal(Terrain.AdvancedGridBrush b,int index,String pair,Portal.Role role){
            b.placePoseAlignedOnSegment(index+1,0.0,0.1,0.1,
                    AddonBlueprint.portal(id+":portal:"+role+":"+index,pair,role));
        }
    }

    private static long mix64(long value) {
        long mixed=value; mixed=(mixed^(mixed>>>30))*0xbf58476d1ce4e5b9L;
        mixed=(mixed^(mixed>>>27))*0x94d049bb133111ebL; return mixed^(mixed>>>31);
    }
}
