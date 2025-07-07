// ReflectorShieldHullmod.java
package data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.util.Misc;
// import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ReflectorShieldHullmod extends BaseHullMod {
    
    // private static final Logger LOG = Logger.getLogger(ReflectorShieldHullmod.class);
    private static final String PLUGIN_KEY = "reflector_shield_plugin";
    private static final Color reflectShieldColorRing = new Color(15, 128, 128,200);
    private static final Color reflectShieldColorInterior = new Color(15, 128, 128,150);
    @Override
    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        // stats.getShieldUpkeepMult().modifyMult(id, 1.5f); // Increase shield upkeep
        // stats.getShieldTurnRateMult().modifyMult(id, 0.8f); // Slightly reduce turn rate
    }
    
    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        ShieldAPI shield = ship.getShield();
        if(shield!=null){
            shield.setRingColor(reflectShieldColorRing);
            shield.setInnerColor(reflectShieldColorInterior);
        }
        
        // Register the ship with the global plugin
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine != null) {
            ReflectorShieldPlugin plugin = (ReflectorShieldPlugin) engine.getCustomData().get(PLUGIN_KEY);
            if (plugin == null) {
                plugin = new ReflectorShieldPlugin();
                engine.getCustomData().put(PLUGIN_KEY, plugin);
                engine.addPlugin(plugin);
            }
            plugin.addReflectorShip(ship);
        }
    }
    
    @Override
    public void unapplyEffectsAfterShipCreation(ShipAPI ship, String id) {
        // Unregister the ship when hullmod is removed
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine != null) {
            ReflectorShieldPlugin plugin = (ReflectorShieldPlugin) engine.getCustomData().get(PLUGIN_KEY);
            if (plugin != null) {
                plugin.removeReflectorShip(ship);
            }
        }
    }
    
    @Override
    public String getDescriptionParam(int index, ShipAPI.HullSize hullSize) {
        switch (index) {
            case 0: return "10%";
            default: return null;
        }
    }
    
    public static class ReflectorShieldPlugin implements EveryFrameCombatPlugin {
        private Set<ShipAPI> reflectorShips = new HashSet<ShipAPI>();
        private Set<DamagingProjectileAPI> processedProjectiles = new HashSet<DamagingProjectileAPI>();
        
        public void addReflectorShip(ShipAPI ship) {
            reflectorShips.add(ship);
            // LOG.info("Added reflector ship: " + ship.getHullSpec().getHullName());
        }
        
        public void removeReflectorShip(ShipAPI ship) {
            reflectorShips.remove(ship);
        }
        
        @Override
        public void advance(float amount, List<InputEventAPI> events) {
            CombatEngineAPI engine = Global.getCombatEngine();
            if (engine == null || engine.isPaused()) return;
            
            // Clean up dead ships
            Set<ShipAPI> deadShips = new HashSet<ShipAPI>();
            for (ShipAPI ship : reflectorShips) {
                if (!engine.isEntityInPlay(ship)) {
                    deadShips.add(ship);
                }
            }
            reflectorShips.removeAll(deadShips);
            
            // Check all projectiles against reflector ships
            for (DamagingProjectileAPI projectile : engine.getProjectiles()) {
                if (processedProjectiles.contains(projectile)) continue;
                if (projectile.getCustomData().containsKey("reflected")) continue;
                
                for (ShipAPI ship : reflectorShips) {
                    ShieldAPI shield = ship.getShield();
                    if(shield!=null){
                        shield.setRingColor(reflectShieldColorRing);
                        shield.setInnerColor(reflectShieldColorInterior);
                    }
        
                    if (shield == null || !shield.isOn()) continue;
                    
                    if (isProjectileHittingShield(projectile, ship)) {
                        // LOG.info("Projectile hitting shield: " + projectile.getProjectileSpecId());
                        reflectProjectile(projectile, ship);
                        processedProjectiles.add(projectile);
                        break;
                    }
                }
            }
            
            // Clean up processed projectiles that are no longer in play
            Set<DamagingProjectileAPI> deadProjectiles = new HashSet<DamagingProjectileAPI>();
            for (DamagingProjectileAPI proj : processedProjectiles) {
                if (!engine.isEntityInPlay(proj)) {
                    deadProjectiles.add(proj);
                }
            }
            processedProjectiles.removeAll(deadProjectiles);
        }
        
        private boolean isProjectileHittingShield(DamagingProjectileAPI projectile, ShipAPI ship) {
            if (projectile.getOwner() == ship.getOwner()) return false; // Don't reflect own projectiles
            
            Vector2f projLoc = projectile.getLocation();
            Vector2f shipLoc = ship.getLocation();
            ShieldAPI shield = ship.getShield();
            
            // Check distance from ship center
            float distance = Misc.getDistance(projLoc, shipLoc);
            float shieldRadius = shield.getRadius();
            
            if (distance > shieldRadius + 50f) return false; // Too far away
            if (distance < shieldRadius - 50f) return false; // Too close (inside shield)
            
            // Check if projectile is within shield arc
            float angleToProjectile = Misc.getAngleInDegrees(shipLoc, projLoc);
            float shieldFacing = shield.getFacing();
            float shieldArc = shield.getActiveArc();
            
            float angleDiff = Misc.getAngleDiff(angleToProjectile, shieldFacing);
            
            return Math.abs(angleDiff) <= shieldArc / 2f;
        }
        
        private void reflectProjectile(DamagingProjectileAPI originalProjectile, ShipAPI ship) {
            CombatEngineAPI engine = Global.getCombatEngine();
            // Get projectile info
            String projectileSpecId = originalProjectile.getProjectileSpecId();
            WeaponAPI sourceWeapon = originalProjectile.getWeapon();
            Vector2f projectilePos = new Vector2f(originalProjectile.getLocation());
            
            // Calculate reflection direction
            Vector2f toShip = Vector2f.sub(ship.getLocation(), projectilePos, null);
            toShip.normalise();
            
            Vector2f originalVel = originalProjectile.getVelocity();
            float speed = originalVel.length();
            
            // Reflect velocity: v' = v - 2(v·n)n where n is the normal
            Vector2f normal = new Vector2f(toShip);
            float dotProduct = Vector2f.dot(originalVel, normal);
            Vector2f reflection = new Vector2f(normal);
            reflection.scale(2 * dotProduct);
            Vector2f reflectedVel = Vector2f.sub(originalVel, reflection, null);
            
            // // Apply 2x hard flux damage
            float damage = originalProjectile.getDamageAmount();
            ship.getFluxTracker().increaseFlux(damage*1.1f, true);
            // LOG.info("Applied extra flux damage: " + damage);
            
            // Remove original projectile
            engine.removeEntity(originalProjectile);
            
            // Create reflected projectile using the original weapon
            // LOG.info("Using original weapon: " + sourceWeapon.getId());
            CombatEntityAPI reflectedProjectile = engine.spawnProjectile(
                ship,
                sourceWeapon,
                sourceWeapon.getId(), // Use weapon ID
                projectilePos,
                Misc.getAngleInDegrees(reflectedVel),
                reflectedVel
            );
            // Mark as reflected
            reflectedProjectile.getCustomData().put("reflected", true);
            
            // Add visual effect
            addReflectionEffect(projectilePos);
            
            // LOG.info("Reflected projectile: " + projectileSpecId);
        }
        
        private void addReflectionEffect(Vector2f location) {
            CombatEngineAPI engine = Global.getCombatEngine();
            
            // Add reflection flash
            engine.addHitParticle(
                location,
                new Vector2f(0, 0),
                30f,
                1f,
                0.3f,
                Color.CYAN
            );
            
            // Add sparks
            for (int i = 0; i < 8; i++) {
                Vector2f sparkVel = Misc.getUnitVectorAtDegreeAngle(i * 45f);
                sparkVel.scale(50f + (float) Math.random() * 100f);
                
                engine.addHitParticle(
                    location,
                    sparkVel,
                    5f,
                    0.8f,
                    0.5f,
                    Color.WHITE
                );
            }
        }
        
        @Override
        public void renderInWorldCoords(ViewportAPI viewport) {}
        
        @Override
        public void renderInUICoords(ViewportAPI viewport) {}
        
        @Override
        public void processInputPreCoreControls(float amount, List<InputEventAPI> events) {}
        
        @Override
        public void init(CombatEngineAPI engine) {}
    }
    
    @Override
    public boolean isApplicableToShip(ShipAPI ship) {
        return ship.getShield() != null;
    }
    
    @Override
    public String getUnapplicableReason(ShipAPI ship) {
        if (ship.getShield() == null) {
            return "Ship has no shield";
        }
        return null;
    }
}