package com.cyclient.module.modules;

import com.cyclient.module.HudModule;
import com.cyclient.setting.BooleanSetting;
import com.cyclient.setting.ModeSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/** Shows your armor (and held item) with durability. */
public class ArmorHudModule extends HudModule {
	private static final int ICON = 16;
	private static final int PAD = 3;

	public final ModeSetting layout = add(new ModeSetting("Layout", "Vertical", "Vertical", "Horizontal"));
	public final ModeSetting durability = add(new ModeSetting("Durability", "Number", "Number", "Percent", "Off"));
	public final BooleanSetting colorDurability = add(new BooleanSetting("Color By Durability", true));
	public final BooleanSetting durabilityBar = add(new BooleanSetting("Item Durability Bar", true));
	public final BooleanSetting showHeld = add(new BooleanSetting("Show Held Item", true));
	public final BooleanSetting showOffhand = add(new BooleanSetting("Show Offhand Item", false));
	public final BooleanSetting bootsFirst = add(new BooleanSetting("Boots First", false));
	public final BooleanSetting hideEmpty = add(new BooleanSetting("Hide Empty Slots", true));

	public ArmorHudModule() {
		super("Armor HUD", "Shows your armor and held item with durability.", true, 4, 202);
	}

	private List<ItemStack> stacks(boolean editor) {
		List<ItemStack> list = new ArrayList<>();
		if (mc.player != null) {
			EquipmentSlot[] order = bootsFirst.get()
					? new EquipmentSlot[]{EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD}
					: new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
			for (EquipmentSlot slot : order) list.add(mc.player.getItemBySlot(slot));
			if (showHeld.get()) list.add(mc.player.getMainHandItem());
			if (showOffhand.get()) list.add(mc.player.getOffhandItem());
		}

		if (hideEmpty.get()) list.removeIf(ItemStack::isEmpty);

		// In the HUD editor, show a sample set if you aren't wearing anything.
		if (editor && list.stream().allMatch(ItemStack::isEmpty)) {
			list.clear();
			list.add(new ItemStack(Items.DIAMOND_HELMET));
			list.add(new ItemStack(Items.DIAMOND_CHESTPLATE));
			list.add(new ItemStack(Items.DIAMOND_LEGGINGS));
			list.add(new ItemStack(Items.DIAMOND_BOOTS));
			if (showHeld.get()) list.add(new ItemStack(Items.DIAMOND_SWORD));
		}
		return list;
	}

	/** Text under/next to an item, or "" for none. */
	private String label(ItemStack stack) {
		if (stack.isEmpty() || durability.is("Off")) return "";
		if (stack.isDamageableItem()) {
			int left = stack.getMaxDamage() - stack.getDamageValue();
			if (durability.is("Percent")) return Math.round(left * 100F / stack.getMaxDamage()) + "%";
			return String.valueOf(left);
		}
		return stack.getCount() > 1 ? String.valueOf(stack.getCount()) : "";
	}

	private int labelColor(ItemStack stack, int x, int y) {
		if (colorDurability.get() && stack.isDamageableItem() && stack.getMaxDamage() > 0) {
			float left = 1F - stack.getDamageValue() / (float) stack.getMaxDamage();
			return left > 0.6F ? 0xFF55FF55 : left > 0.25F ? 0xFFFFFF55 : 0xFFFF5555;
		}
		return valueTextColor(x, y);
	}

	private boolean vertical() {
		return layout.is("Vertical");
	}

	private int maxLabelWidth(List<ItemStack> list) {
		int w = 0;
		for (ItemStack s : list) w = Math.max(w, font().width(label(s)));
		return w;
	}

	private int cellWidth(List<ItemStack> list) {
		return Math.max(ICON, maxLabelWidth(list)) + 4;
	}

	@Override
	public int getWidth() {
		List<ItemStack> list = stacks(true);
		if (vertical()) {
			int labelW = maxLabelWidth(list);
			return PAD + ICON + (labelW > 0 ? 3 + labelW : 0) + PAD;
		}
		return PAD * 2 + Math.max(1, list.size()) * cellWidth(list);
	}

	@Override
	public int getHeight() {
		List<ItemStack> list = stacks(true);
		if (vertical()) return PAD * 2 + Math.max(1, list.size()) * (ICON + 2) - 2;
		boolean labels = maxLabelWidth(list) > 0;
		return PAD * 2 + ICON + (labels ? 10 : 0);
	}

	@Override
	public void render(GuiGraphicsExtractor g, int x, int y, boolean editor) {
		List<ItemStack> list = stacks(editor);
		if (list.isEmpty()) return;
		drawBox(g, x, y, getWidth(), getHeight());

		int cell = cellWidth(list);
		for (int i = 0; i < list.size(); i++) {
			ItemStack stack = list.get(i);
			int ix, iy;
			if (vertical()) {
				ix = x + PAD;
				iy = y + PAD + i * (ICON + 2);
			} else {
				ix = x + PAD + i * cell + (cell - ICON) / 2;
				iy = y + PAD;
			}
			if (stack.isEmpty()) continue;

			g.item(stack, ix, iy);
			if (durabilityBar.get() && stack.isDamageableItem()) {
				g.itemDecorations(font(), stack, ix, iy, ""); // "" = no count, just the durability bar
			}

			String text = label(stack);
			if (text.isEmpty()) continue;
			int tx, ty;
			if (vertical()) {
				tx = ix + ICON + 3;
				ty = iy + (ICON - font().lineHeight) / 2 + 1;
			} else {
				tx = x + PAD + i * cell + (cell - font().width(text)) / 2;
				ty = iy + ICON + 1;
			}
			drawText(g, text, tx, ty, labelColor(stack, tx, ty));
		}
	}
}
