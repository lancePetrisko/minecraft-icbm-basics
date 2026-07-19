package com.example.icbmbasics.registry;

import com.example.icbmbasics.ICBMBasics;
import com.example.icbmbasics.entity.CiwsBulletEntity;
import com.example.icbmbasics.entity.MissileEntity;
import com.example.icbmbasics.entity.SamInterceptorEntity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;

public final class ModEntities {
	public static final RegistryKey<EntityType<?>> MISSILE_KEY =
			RegistryKey.of(RegistryKeys.ENTITY_TYPE, ICBMBasics.id("missile"));
	public static final RegistryKey<EntityType<?>> SAM_INTERCEPTOR_KEY =
			RegistryKey.of(RegistryKeys.ENTITY_TYPE, ICBMBasics.id("sam_interceptor"));
	public static final RegistryKey<EntityType<?>> CIWS_BULLET_KEY =
			RegistryKey.of(RegistryKeys.ENTITY_TYPE, ICBMBasics.id("ciws_bullet"));

	public static final EntityType<MissileEntity> MISSILE =
			Registry.register(Registries.ENTITY_TYPE, MISSILE_KEY,
					EntityType.Builder.<MissileEntity>create(MissileEntity::new, SpawnGroup.MISC)
							.dimensions(0.6f, 0.6f)
							.makeFireImmune()
							.maxTrackingRange(160)
							.trackingTickInterval(1)
							.build(MISSILE_KEY));

	public static final EntityType<SamInterceptorEntity> SAM_INTERCEPTOR =
			Registry.register(Registries.ENTITY_TYPE, SAM_INTERCEPTOR_KEY,
					EntityType.Builder.<SamInterceptorEntity>create(SamInterceptorEntity::new, SpawnGroup.MISC)
							.dimensions(0.4f, 0.4f)
							.makeFireImmune()
							.maxTrackingRange(160)
							.trackingTickInterval(1)
							.build(SAM_INTERCEPTOR_KEY));

	public static final EntityType<CiwsBulletEntity> CIWS_BULLET =
			Registry.register(Registries.ENTITY_TYPE, CIWS_BULLET_KEY,
					EntityType.Builder.<CiwsBulletEntity>create(CiwsBulletEntity::new, SpawnGroup.MISC)
							.dimensions(0.15f, 0.15f)
							.makeFireImmune()
							.maxTrackingRange(160)
							.trackingTickInterval(1)
							.build(CIWS_BULLET_KEY));

	private ModEntities() {
	}

	public static void register() {
	}
}
