package com.tom.cpm.common;

import net.minecraft.server.level.ServerPlayer;

import virtuoel.pehkui.api.ScaleData;
import virtuoel.pehkui.api.ScaleTypes;

public class PehkuiInterface {

	public static void setScale(ServerPlayer player, float newScale) {
		ScaleData scaleData = ScaleTypes.BASE.getScaleData(player);
		scaleData.setTargetScale(newScale);
		scaleData.setScale(newScale);
		scaleData.setScale(newScale);
		scaleData.tick();
		scaleData.onUpdate();
	}

}