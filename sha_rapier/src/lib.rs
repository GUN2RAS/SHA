use rapier3d::prelude::*;
use rapier3d::control::{KinematicCharacterController, CharacterLength, CharacterAutostep};
use std::sync::Mutex;
use std::collections::HashMap;

lazy_static::lazy_static! {
    static ref WORLDS: Mutex<HashMap<u32, PhysicsWorld>> = Mutex::new(HashMap::new());
}

static mut NEXT_WORLD_ID: u32 = 1;

struct PhysicsWorld {
    rigid_body_set: RigidBodySet,
    collider_set: ColliderSet,
    integration_parameters: IntegrationParameters,
    physics_pipeline: PhysicsPipeline,
    island_manager: IslandManager,
    broad_phase: DefaultBroadPhase,
    narrow_phase: NarrowPhase,
    impulse_joint_set: ImpulseJointSet,
    multibody_joint_set: MultibodyJointSet,
    ccd_solver: CCDSolver,
    query_pipeline: QueryPipeline,
    gravity: Vector<f32>,
    handle_map: HashMap<u32, RigidBodyHandle>,
    next_handle_id: u32,
}

impl PhysicsWorld {
    fn new() -> Self {
        Self {
            rigid_body_set: RigidBodySet::new(),
            collider_set: ColliderSet::new(),
            integration_parameters: IntegrationParameters::default(),
            physics_pipeline: PhysicsPipeline::new(),
            island_manager: IslandManager::new(),
            broad_phase: DefaultBroadPhase::new(),
            narrow_phase: NarrowPhase::new(),
            impulse_joint_set: ImpulseJointSet::new(),
            multibody_joint_set: MultibodyJointSet::new(),
            ccd_solver: CCDSolver::new(),
            query_pipeline: QueryPipeline::new(),
            gravity: vector![0.0, -9.81, 0.0],
            handle_map: HashMap::new(),
            next_handle_id: 1,
        }
    }

    fn step(&mut self) {
        let physics_hooks = ();
        let event_handler = ();

        self.physics_pipeline.step(
            &self.gravity,
            &self.integration_parameters,
            &mut self.island_manager,
            &mut self.broad_phase,
            &mut self.narrow_phase,
            &mut self.rigid_body_set,
            &mut self.collider_set,
            &mut self.impulse_joint_set,
            &mut self.multibody_joint_set,
            &mut self.ccd_solver,
            Some(&mut self.query_pipeline),
            &physics_hooks,
            &event_handler,
        );
    }
}

/// Creates a new physics world and returns its ID.
#[no_mangle]
pub extern "C" fn rapier_create_world() -> u32 {
    let mut worlds = WORLDS.lock().unwrap();
    let id = unsafe {
        let current = NEXT_WORLD_ID;
        NEXT_WORLD_ID += 1;
        current
    };
    worlds.insert(id, PhysicsWorld::new());
    id
}

/// Destroys a physics world by its ID.
#[no_mangle]
pub extern "C" fn rapier_destroy_world(world_id: u32) {
    let mut worlds = WORLDS.lock().unwrap();
    worlds.remove(&world_id);
}

/// Steps the simulation by 1 tick (default 60Hz).
#[no_mangle]
pub extern "C" fn rapier_step(world_id: u32) {
    let mut worlds = WORLDS.lock().unwrap();
    if let Some(world) = worlds.get_mut(&world_id) {
        world.step();
    }
}

/// Adds a dynamic rigidbody (box collider) and returns its mapped ID
#[no_mangle]
pub extern "C" fn rapier_add_dynamic_box(world_id: u32, x: f32, y: f32, z: f32, hx: f32, hy: f32, hz: f32) -> u32 {
    let mut worlds = WORLDS.lock().unwrap();
    if let Some(world) = worlds.get_mut(&world_id) {
        let rigid_body = RigidBodyBuilder::dynamic().translation(vector![x, y, z]).build();
        let collider = ColliderBuilder::cuboid(hx, hy, hz).restitution(0.7).build();
        
        let body_handle = world.rigid_body_set.insert(rigid_body);
        world.collider_set.insert_with_parent(collider, body_handle, &mut world.rigid_body_set);
        
        let id = world.next_handle_id;
        world.next_handle_id += 1;
        world.handle_map.insert(id, body_handle);
        return id;
    }
    0
}

/// Adds a static infinite plane along the X and Z axes at the given Y level
#[no_mangle]
pub extern "C" fn rapier_add_static_plane(world_id: u32, y_level: f32) -> u32 {
    let mut worlds = WORLDS.lock().unwrap();
    if let Some(world) = worlds.get_mut(&world_id) {
        let collider = ColliderBuilder::halfspace(Vector::y_axis()).translation(vector![0.0, y_level, 0.0]).build();
        let handle = world.collider_set.insert(collider);
        return handle.into_raw_parts().0;
    }
    0
}

const GROUP_VSE: u32 = 0b0001;
const GROUP_TERRAIN: u32 = 0b0010;

/// Adds an immovable static cuboid collider to represent Minecraft terrain blocks
#[no_mangle]
pub extern "C" fn rapier_add_static_box(world_id: u32, x: f32, y: f32, z: f32, hx: f32, hy: f32, hz: f32) -> i64 {
    let mut worlds = WORLDS.lock().unwrap();
    if let Some(world) = worlds.get_mut(&world_id) {
        let collider = ColliderBuilder::cuboid(hx, hy, hz)
            .translation(vector![x, y, z])
            .restitution(0.1) // minimal bounce for terrain
            .friction(0.8) // relatively high friction for terrain
            .collision_groups(InteractionGroups::new(Group::from_bits_truncate(GROUP_TERRAIN), Group::all()))
            .build();
        let handle = world.collider_set.insert(collider);
        let (idx, gen) = handle.into_raw_parts();
        let packed = (idx as u64) | ((gen as u64) << 32);
        return packed as i64;
    }
    -1
}

/// Consumes a direct memory array of localized block coordinates (x, y, z as i32) and builds a single Compound RigidBody
#[no_mangle]
pub unsafe extern "C" fn rapier_add_dynamic_structure(
    world_id: u32,
    root_x: f32,
    root_y: f32,
    root_z: f32,
    blocks_ptr: *const i32,
    block_count: usize,
    out_handles_ptr: *mut i64,
) -> i32 {
    let mut worlds = WORLDS.lock().unwrap();
    if let Some(world) = worlds.get_mut(&world_id) {
        let rigid_body = RigidBodyBuilder::dynamic()
            .translation(vector![root_x, root_y, root_z])
            .ccd_enabled(true)
            .build();
        let body_handle = world.rigid_body_set.insert(rigid_body);

        let coords_slice = std::slice::from_raw_parts(blocks_ptr, block_count * 3);
        
        for i in 0..block_count {
            let bx = coords_slice[i * 3];
            let by = coords_slice[i * 3 + 1];
            let bz = coords_slice[i * 3 + 2];
            
            let shape = SharedShape::cuboid(0.5, 0.5, 0.5);
            let iso = Isometry::translation(bx as f32 + 0.5, by as f32 + 0.5, bz as f32 + 0.5);
            
            let collider = ColliderBuilder::new(shape).position(iso)
                .restitution(0.3)
                .friction(0.6)
                .density(1.0)
                .collision_groups(InteractionGroups::new(Group::from_bits_truncate(GROUP_VSE), Group::all()))
                .build();
                
            let coll_handle = world.collider_set.insert_with_parent(collider, body_handle, &mut world.rigid_body_set);
            
            if !out_handles_ptr.is_null() {
                let (idx, gen) = coll_handle.into_raw_parts();
                let packed = (idx as u64) | ((gen as u64) << 32);
                *out_handles_ptr.offset(i as isize) = packed as i64;
            }
        }

        let id = world.next_handle_id;
        world.next_handle_id += 1;
        world.handle_map.insert(id, body_handle);
        return id as i32;
    }
    -1
}

#[no_mangle]
pub unsafe extern "C" fn rapier_add_block_to_structure(world_id: u32, handle_id: i32, bx: i32, by: i32, bz: i32) -> i64 {
    let mut worlds = WORLDS.lock().unwrap();
    if let Some(world) = worlds.get_mut(&world_id) {
        if let Some(body_handle) = world.handle_map.get(&(handle_id as u32)) {
            let shape = SharedShape::cuboid(0.5, 0.5, 0.5);
            let iso = Isometry::translation(bx as f32 + 0.5, by as f32 + 0.5, bz as f32 + 0.5);
            
            let collider = ColliderBuilder::new(shape).position(iso)
                .restitution(0.3)
                .friction(0.6)
                .density(1.0)
                .collision_groups(InteractionGroups::new(Group::from_bits_truncate(GROUP_VSE), Group::all()))
                .build();
                
            let coll_handle = world.collider_set.insert_with_parent(collider, *body_handle, &mut world.rigid_body_set);
            let (idx, gen) = coll_handle.into_raw_parts();
            let packed = (idx as u64) | ((gen as u64) << 32);
            return packed as i64;
        }
    }
    -1
}

/// Populates [x, y, z] to the provided pointer
#[no_mangle]
pub unsafe extern "C" fn rapier_get_position(world_id: u32, handle_id: i32, out_pos: *mut f32) {
    let mut worlds = WORLDS.lock().unwrap();
    if let Some(world) = worlds.get_mut(&world_id) {
        let handle = world.handle_map.get(&(handle_id as u32));
        if let Some(handle) = handle {
            if let Some(body) = world.rigid_body_set.get(*handle) {
                let trans = body.translation();
                *out_pos.offset(0) = trans.x;
                *out_pos.offset(1) = trans.y;
                *out_pos.offset(2) = trans.z;
            }
        }
    }
}

/// Populates [x, y, z, w] to the provided pointer
#[no_mangle]
pub unsafe extern "C" fn rapier_get_rotation(world_id: u32, handle_id: i32, out_rot: *mut f32) {
    let mut worlds = WORLDS.lock().unwrap();
    if let Some(world) = worlds.get_mut(&world_id) {
        let handle = world.handle_map.get(&(handle_id as u32));
        if let Some(handle) = handle {
            if let Some(body) = world.rigid_body_set.get(*handle) {
                let rot = body.rotation().quaternion();
                *out_rot.offset(0) = rot.i;
                *out_rot.offset(1) = rot.j;
                *out_rot.offset(2) = rot.k;
                *out_rot.offset(3) = rot.w;
            }
        }
    }
}

/// Sets the [x, y, z, w] quaternion rotation of a body
#[no_mangle]
pub unsafe extern "C" fn rapier_set_rotation(world_id: u32, handle_id: i32, x: f32, y: f32, z: f32, w: f32) {
    let mut worlds = WORLDS.lock().unwrap();
    if let Some(world) = worlds.get_mut(&world_id) {
        if let Some(handle) = world.handle_map.get(&(handle_id as u32)) {
            if let Some(body) = world.rigid_body_set.get_mut(*handle) {
                body.set_rotation(rapier3d::na::UnitQuaternion::from_quaternion(rapier3d::na::Quaternion::new(w, x, y, z)), true);
            }
        }
    }
}

/// Sets the [x, y, z] linear velocity of a body
#[no_mangle]
pub unsafe extern "C" fn rapier_set_linear_velocity(world_id: u32, handle_id: i32, vx: f32, vy: f32, vz: f32) {
    let mut worlds = WORLDS.lock().unwrap();
    if let Some(world) = worlds.get_mut(&world_id) {
        if let Some(handle) = world.handle_map.get(&(handle_id as u32)) {
            if let Some(body) = world.rigid_body_set.get_mut(*handle) {
                body.set_linvel(vector![vx, vy, vz], true);
            }
        }
    }
}

/// Evaluates the movement delta for a Kinematic Character Controller (player) colliding with the Rapier simulation
#[no_mangle]
pub unsafe extern "C" fn rapier_move_character(
    world_id: u32,
    pos_x: f32, pos_y: f32, pos_z: f32,
    hx: f32, hy: f32,
    mov_x: f32, mov_y: f32, mov_z: f32,
    step_height: f32,
    out_mov: *mut f32
) {
    let mut worlds = WORLDS.lock().unwrap();
    if let Some(world) = worlds.get_mut(&world_id) {
        world.query_pipeline.update(&world.collider_set);

        let mut character_controller = KinematicCharacterController {
            offset: CharacterLength::Absolute(0.01),
            ..KinematicCharacterController::default()
        };
        
        if step_height > 0.0 {
            character_controller.autostep = Some(CharacterAutostep {
                max_height: CharacterLength::Absolute(step_height),
                min_width: CharacterLength::Absolute(0.01),
                include_dynamic_bodies: true,
            });
        }

        let shape = SharedShape::cuboid(hx, hy, hx);
        let pos = Isometry::translation(pos_x, pos_y, pos_z);
        let desired_translation = vector![mov_x, mov_y, mov_z];

        // Explicitly ONLY query against VSE geometry (ignore duplicate static terrain which causes cobweb)
        let filter = QueryFilter::new()
            .exclude_sensors()
            .groups(InteractionGroups::new(Group::all(), Group::from_bits_truncate(GROUP_VSE)));

        let effective_movement = character_controller.move_shape(
            0.05,
            &world.rigid_body_set,
            &world.collider_set,
            &world.query_pipeline,
            &*shape,
            &pos,
            desired_translation,
            filter,
            |_| {},
        );

        *out_mov.offset(0) = effective_movement.translation.x;
        *out_mov.offset(1) = effective_movement.translation.y;
        *out_mov.offset(2) = effective_movement.translation.z;
    } else {
        *out_mov.offset(0) = f32::NAN;
    }
}

/// Removes a dynamic structure from the physics world to prevent memory leaks and overlapping explosive collisions upon chunk unloads
#[no_mangle]
pub unsafe extern "C" fn rapier_remove_dynamic_structure(world_id: u32, handle_id: i32) {
    let mut worlds = WORLDS.lock().unwrap();
    if let Some(world) = worlds.get_mut(&world_id) {
        if let Some(handle) = world.handle_map.remove(&(handle_id as u32)) {
            world.rigid_body_set.remove(
                handle,
                &mut world.island_manager,
                &mut world.collider_set,
                &mut world.impulse_joint_set,
                &mut world.multibody_joint_set,
                true,
            );
        }
    }
}

/// Removes a standalone static collider (terrain box) by its packed 64-bit handle
#[no_mangle]
pub extern "C" fn rapier_remove_collider(world_id: u32, collider_raw: i64) {
    let mut worlds = WORLDS.lock().unwrap();
    if let Some(world) = worlds.get_mut(&world_id) {
        let raw_u64 = collider_raw as u64;
        let idx = (raw_u64 & 0xFFFFFFFF) as u32;
        let gen = (raw_u64 >> 32) as u32;
        let handle = ColliderHandle::from_raw_parts(idx, gen);
        if world.collider_set.contains(handle) {
            world.collider_set.remove(handle, &mut world.island_manager, &mut world.rigid_body_set, true);
        }
    }
}

