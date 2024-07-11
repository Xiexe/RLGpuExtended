
package com.gpuExtended;

import net.runelite.client.config.*;

import static com.gpuExtended.GpuExtendedPlugin.MAX_DISTANCE;
import static com.gpuExtended.GpuExtendedPlugin.MAX_FOG_DEPTH;
import com.gpuExtended.config.AntiAliasingMode;
import com.gpuExtended.config.ColorBlindMode;
import com.gpuExtended.config.UIScalingMode;

import java.awt.*;

@ConfigGroup(GpuExtendedConfig.GROUP)
public interface GpuExtendedConfig extends Config
{
	String GROUP = "gpu";

	// <editor-fold defaultstate="collapsed" desc="General Settings">
		@ConfigSection(
				name = "General",
				description = "General Settings",
				position = 0
		)
		String generalSettings = "generalSettings";

		@Range(
			max = MAX_DISTANCE
		)
		@ConfigItem(
			keyName = "drawDistance",
			name = "Draw Distance",
			description = "Draw distance. Requires compute shaders to be enabled.",
			position = 1,
			section = generalSettings
		)
		default int drawDistance()
		{
			return 50;
		}

		@ConfigItem(
			keyName = "hideUnrelatedMaps",
			name = "Hide unrelated maps",
			description = "Hide unrelated map areas you shouldn't see.",
			position = 2,
			section = generalSettings
		)
		default boolean hideUnrelatedMaps()
		{
			return true;
		}

		@Range(
			max = 5
		)
		@ConfigItem(
			keyName = "expandedMapLoadingChunks",
			name = "Extended map loading",
			description = "Extra map area to load, in 8 tile chunks.",
			position = 1,
			section = generalSettings
		)
		default int expandedMapLoadingChunks()
		{
			return 3;
		}

		@ConfigItem(
			keyName = "smoothBanding",
			name = "Remove Color Banding",
			description = "Smooths out the color banding that is present in the CPU renderer",
			position = 2,
			section = generalSettings
		)
		default boolean smoothBanding()
		{
			return true;
		}

		@ConfigItem(
			keyName = "antiAliasingMode",
			name = "Anti Aliasing",
			description = "Configures the anti-aliasing mode",
			position = 3,
			section = generalSettings
		)
		default AntiAliasingMode antiAliasingMode()
		{
			return AntiAliasingMode.MSAA_2;
		}

		@ConfigItem(
			keyName = "uiScalingMode",
			name = "UI scaling mode",
			description = "Sampling function to use for the UI in stretched mode",
			position = 4,
			section = generalSettings
		)
		default UIScalingMode uiScalingMode()
		{
			return UIScalingMode.LINEAR;
		}

		@Range(
			max = MAX_FOG_DEPTH
		)
		@ConfigItem(
			keyName = "fogDepth",
			name = "Fog depth",
			description = "Distance from the scene edge the fog starts",
			position = 5,
			section = generalSettings
		)
		default int fogDepth()
		{
			return 0;
		}

		@Range(
			max = 16
		)
		@ConfigItem(
			keyName = "anisotropicFilteringLevel",
			name = "Anisotropic Filtering",
			description = "Configures the anisotropic filtering level.",
			position = 7,
			section = generalSettings
		)
		default int anisotropicFilteringLevel()
		{
			return 1;
		}

		@ConfigItem(
			keyName = "colorBlindMode",
			name = "Colorblindness Correction",
			description = "Adjusts colors to account for colorblindness",
			position = 8,
			section = generalSettings
		)
		default ColorBlindMode colorBlindMode()
		{
			return ColorBlindMode.NONE;
		}

		@ConfigItem(
			keyName = "unlockFps",
			name = "Unlock FPS",
			description = "Removes the 50 FPS cap for camera movement",
			position = 10,
			section = generalSettings
		)
		default boolean unlockFps()
		{
			return true;
		}

		enum SyncMode
		{
			OFF,
			ON,
			ADAPTIVE
		}

		@ConfigItem(
			keyName = "vsyncMode",
			name = "Vsync Mode",
			description = "Method to synchronize frame rate with refresh rate",
			position = 11,
			section = generalSettings
		)
		default SyncMode syncMode()
		{
			return SyncMode.OFF;
		}

		@ConfigItem(
			keyName = "fpsTarget",
			name = "FPS Target",
			description = "Target FPS when unlock FPS is enabled and Vsync mode is OFF",
			position = 12,
			section = generalSettings
		)
		@Range(
			min = 1,
			max = 999
		)
		default int fpsTarget()
		{
			return 120;
		}
	// </editor-fold>

	// <editor-fold defaultstate="collapsed" desc="GPU True Tile Settings">
		@ConfigSection(
				name = "GPU True Tile",
				description = "Tile markers that are rendered on the GPU. Allows tile markers to be depth sorted properly.",
				position = 1,
				closedByDefault = true
		)
		String gpuTrueTile = "gpuTrueTile";

		@ConfigItem(
				keyName = "highlightTrueTile",
				name = "Highlight True Tile",
				description = "Highlights the true tile that the player is standing on.",
				position = 0,
				section = gpuTrueTile
		)
		default boolean highlightTrueTile() { return false; }

		@ConfigItem(
				keyName = "trueTileFadeOut",
				name = "Fade Out",
				description = "Fades out the true tile that the player is standing on after some time.",
				position = 1,
				section = gpuTrueTile
		)
		default boolean trueTileFadeOut() { return true; }

		@ConfigItem(
				keyName = "trueTileFadeOutTime",
				name = "Fade Out Time",
				description = "Time in milliseconds that it takes the true tile to fade out.",
				position = 2,
				section = gpuTrueTile
		)
		default int trueTileFadeOutTime() { return 1500; }

		@ConfigItem(
				keyName = "trueTileCornerLength",
				name = "Corner Length",
				description = "How far border lines should be drawn from the corners.",
				position = 3,
				section = gpuTrueTile
		)
		@Range(
				max = 64
		)
		default int trueTileCornerLength() { return 64; }

		@Alpha
		@ConfigItem(
				keyName = "trueTileFillColor",
				name = "Fill Color",
				description = "Changes the fill color of the true tile.",
				position = 4,
				section = gpuTrueTile
		)
		default Color trueTileFillColor() { return new Color(0f, 0f, 0f, 0.25f); }

		@Alpha
		@ConfigItem(
				keyName = "trueTileBorderColor",
				name = "Border Color",
				description = "Changes the border color of the true tile.",
				position = 5,
				section = gpuTrueTile
		)
		default Color trueTileBorderColor() { return new Color(0f, 1f, 1f, 0.25f); }
	// </editor-fold>

	// <editor-fold defaultstate="collapsed" desc="GPU Hovered Tile Settings">
		@ConfigSection(
				name = "GPU Hovered Tile",
				description = "Tile markers that are rendered on the GPU. Allows tile markers to be depth sorted properly.",
				position = 2,
				closedByDefault = true
		)
		String gpuHoveredTile = "gpuHoveredTile";

		@ConfigItem(
				keyName = "highlightHoveredTile",
				name = "Highlight Hovered Tile",
				description = "Highlights the tile that the mouse is hovering over.",
				position = 0,
				section = gpuHoveredTile
		)
		default boolean highlightHoveredTile() { return false; }

		@ConfigItem(
				keyName = "hoveredTileCornerLength",
				name = "Corner Length",
				description = "How far border lines should be drawn from the corners.",
				position = 3,
				section = gpuHoveredTile
		)
		@Range(
				max = 64
		)
		default int hoveredTileCornerLength() { return 64; }

		@Alpha
		@ConfigItem(
				keyName = "hoveredTileFillColor",
				name = "Fill Color",
				description = "Changes the fill color of the true tile.",
				position = 1,
				section = gpuHoveredTile
		)
		default Color hoveredTileFillColor() { return new Color(0f, 0f, 0f, 0.25f); }

		@Alpha
		@ConfigItem(
				keyName = "hoveredTileBorderColor",
				name = "Border Color",
				description = "Changes the border color of the true tile.",
				position = 2,
				section = gpuHoveredTile
		)
		default Color hoveredTileBorderColor() { return new Color(0f, 1f, 1f, 0.25f); }
	// </editor-fold>

	// <editor-fold defaultstate="collapsed" desc="GPU Destination Tile Settings">
		@ConfigSection(
				name = "GPU Destination Tile",
				description = "Tile markers that are rendered on the GPU. Allows tile markers to be depth sorted properly.",
				position = 2,
				closedByDefault = true
		)
		String gpuDestTile = "gpuDestTile";

		@ConfigItem(
				keyName = "highlightDestinationTile",
				name = "Highlight Destination Tile",
				description = "Highlights the tile that the player is walking to.",
				position = 0,
				section = gpuDestTile
		)
		default boolean highlightDestinationTile() { return false; }

		@ConfigItem(
				keyName = "destinationTileCornerLength",
				name = "Corner Length",
				description = "How far border lines should be drawn from the corners.",
				position = 3,
				section = gpuDestTile
		)
		@Range(
				max = 64
		)
		default int destinationTileCornerLength() { return 64; }

		@Alpha
		@ConfigItem(
				keyName = "destinationTileFillColor",
				name = "Fill Color",
				description = "Changes the fill color of the true tile.",
				position = 1,
				section = gpuDestTile
		)
		default Color destinationTileFillColor() { return new Color(0f, 0f, 0f, 0.25f); }

		@Alpha
		@ConfigItem(
				keyName = "destinationTileBorderColor",
				name = "Border Color",
				description = "Changes the border color of the true tile.",
				position = 2,
				section = gpuDestTile
		)
		default Color destinationTileBorderColor() { return new Color(0f, 1f, 1f, 0.25f); }
	// </editor-fold>

	// <editor-fold defaultstate="collapsed" desc="Lighting Settings">
		@ConfigSection(
				name = "Lighting",
				description = "Lighting settings",
				position = 100,
				closedByDefault = true
		)
		String lightSettings = "lightSettings";

		@ConfigItem(
				keyName = "skyColor",
				name = "Sky Color",
				description = "",
				position = 98,
				section = lightSettings
		)
		Color skyColor();

		@ConfigItem(
				keyName = "overrideLightRotation",
				name = "Custom Sun Rotation",
				description = "",
				position = 100,
				section = lightSettings
		)
		default boolean customLightRotation()
		{
			return false;
		}

		@ConfigItem(
				keyName = "lightPitch",
				name = "Sun Pitch",
				description = "",
				position = 100,
				section = lightSettings
		)
		default int lightPitch()
		{
			return 50;
		}

		@ConfigItem(
				keyName = "lightYaw",
				name = "Sun Yaw",
				description = "",
				position = 100,
				section = lightSettings
		)
		default int lightYaw()
		{
			return 50;
		}
	// </editor-fold>

	// <editor-fold defaultstate="collapsed" desc="Experimental Settings">
		@ConfigSection(
				name = "Experimental",
				description = "Experimental settings that may not work correctly or at all.",
				position = 200,
				closedByDefault = true
		)
		String experimentalSettings = "experimental";

		@ConfigItem(
				keyName = "roofFading",
				name = "Roof Fading",
				description = "Forces the client to render roofs, but smoothly fades them out when they are in the way, rather than snapping them on / off.\n" +
						"Please disable the Roof Removal plugin, as they might interfere with each other.",
				position = 1,
				section = experimentalSettings
		)
		default boolean roofFading() { return false; }

		@ConfigItem(
				keyName = "roofFadingRange",
				name = "Roof Fading Range",
				description = "Changes the range at which roofs start to fade out.",
				position = 2,
				section = experimentalSettings
		)
		default int roofFadingRange() { return 15; }
	// </editor-fold>

	// <editor-fold defaultstate="collapsed" desc="Debugging Settings">
		@ConfigSection(
				name = "Debugging",
				description = "Debugging",
				position = 999,
				closedByDefault = true
		)
		String debugging = "debugging";

		@ConfigItem(
				keyName = "showShadowMap",
				name = "Show Shadow Map",
				description = "",
				position = 0,
				section = debugging
		)
		default boolean showShadowMap()
		{
			return false;
		}
	// </editor-fold>
}
