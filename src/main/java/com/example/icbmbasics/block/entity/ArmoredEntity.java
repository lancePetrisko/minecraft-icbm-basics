package com.example.icbmbasics.block.entity;

/** Implemented by any block entity that absorbs missile hits (armored blocks, armored doors). */
public interface ArmoredEntity {
	/** Called once per missile impact within range. Advances damage and may break the block. */
	void applyMissileHit();
}
