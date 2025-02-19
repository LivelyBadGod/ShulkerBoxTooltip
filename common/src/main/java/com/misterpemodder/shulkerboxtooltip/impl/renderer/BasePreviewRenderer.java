package com.misterpemodder.shulkerboxtooltip.impl.renderer;

import com.misterpemodder.shulkerboxtooltip.ShulkerBoxTooltipClient;
import com.misterpemodder.shulkerboxtooltip.api.PreviewContext;
import com.misterpemodder.shulkerboxtooltip.api.PreviewType;
import com.misterpemodder.shulkerboxtooltip.api.config.PreviewConfiguration;
import com.misterpemodder.shulkerboxtooltip.api.provider.EmptyPreviewProvider;
import com.misterpemodder.shulkerboxtooltip.api.provider.PreviewProvider;
import com.misterpemodder.shulkerboxtooltip.api.renderer.PreviewRenderer;
import com.misterpemodder.shulkerboxtooltip.impl.util.MergedItemStack;
import com.misterpemodder.shulkerboxtooltip.impl.util.ShulkerBoxTooltipUtil;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public abstract class BasePreviewRenderer implements PreviewRenderer {
  protected PreviewType previewType;
  protected PreviewConfiguration config;
  protected int compactMaxRowSize;
  protected int maxRowSize;
  protected Identifier textureOverride;
  protected PreviewProvider provider;
  protected List<MergedItemStack> items;
  protected PreviewContext previewContext;

  private final int slotWidth;
  private final int slotHeight;
  private final int slotXOffset;
  private final int slotYOffset;

  protected BasePreviewRenderer(int slotWidth, int slotHeight, int slotXOffset, int slotYOffset) {
    this.items = new ArrayList<>();
    this.previewType = PreviewType.FULL;
    this.maxRowSize = 9;

    this.slotWidth = slotWidth;
    this.slotHeight = slotHeight;
    this.slotXOffset = slotXOffset;
    this.slotYOffset = slotYOffset;

    var world = ShulkerBoxTooltipClient.client == null ? null : ShulkerBoxTooltipClient.client.world;

    this.setPreview(PreviewContext.builder(ItemStack.EMPTY)
        .withRegistryLookup(world == null ? null : world.getRegistryManager())
        .build(), EmptyPreviewProvider.INSTANCE);
  }

  protected int getMaxRowSize() {
    return this.previewType == PreviewType.COMPACT ? this.compactMaxRowSize : this.maxRowSize;
  }

  protected int getInvSize() {
    return this.previewType == PreviewType.COMPACT ? Math.max(1, this.items.size()) : this.provider.getInventoryMaxSize(
        this.previewContext);
  }

  @Override
  public void setPreviewType(PreviewType type) {
    this.previewType = type;
  }

  @Override
  public void setPreview(PreviewContext context, PreviewProvider provider) {
    List<ItemStack> inventory = provider.getInventory(context);
    int rowSize = provider.getMaxRowSize(context);

    this.config = context.config();
    this.compactMaxRowSize = this.config.defaultMaxRowSize();
    if (this.compactMaxRowSize <= 0)
      this.compactMaxRowSize = 9;
    if (rowSize <= 0)
      rowSize = this.compactMaxRowSize;
    this.maxRowSize = rowSize;
    this.textureOverride = provider.getTextureOverride(context);
    this.provider = provider;
    this.items = MergedItemStack.mergeInventory(inventory, provider.getInventoryMaxSize(context),
        this.config.itemStackMergingStrategy());
    this.previewContext = context;
  }

  /**
   * Get the slot id at the given coordinates if X and Y are in bounds of the preview window
   *
   * @return The slot id at the given coordinates, or -1 if not found.
   */
  private int getSlotAt(int x, int y) {
    int slot = -1;

    // Get the slot id at the given coordinates if X and Y are in bounds of the preview window
    if (x + 1 >= this.slotXOffset && y + 1 >= this.slotYOffset) {
      int maxRowSize = this.getMaxRowSize();
      int slotX = (x + 1 - this.slotXOffset) / this.slotWidth;
      int slotY = (y + 1 - this.slotYOffset) / this.slotHeight;

      if (slotX < maxRowSize)
        slot = slotX + slotY * maxRowSize;
    }

    return slot;
  }

  /**
   * @param x Top-left corner X coordinate of the preview window
   * @param y Top-left corner Y coordinate of the preview window
   * @return The item stack at the given coordinates, or {@link ItemStack#EMPTY} if not found.
   */
  private ItemStack getStackAt(int x, int y) {
    int slot = this.getSlotAt(x, y);

    if (this.previewType == PreviewType.COMPACT) {
      if (slot < 0 || slot >= this.items.size())
        return ItemStack.EMPTY;
      MergedItemStack merged = this.items.get(slot);

      return merged == null ? ItemStack.EMPTY : merged.get();
    } else {
      for (MergedItemStack merged : this.items) {
        ItemStack stack = merged.getSubStack(slot);
        if (!stack.isEmpty())
          return stack;
      }
      return ItemStack.EMPTY;
    }
  }

  private void drawItem(ItemStack stack, int x, int y, DrawContext context, TextRenderer textRenderer, int slot,
      boolean shortItemCount) {
    String countLabel = "";
    int maxRowSize = this.getMaxRowSize();

    // stack size might exceed the maximum, so we create our own count label instead of the default
    if (stack.getCount() != 1) {
      if (shortItemCount)
        countLabel = ShulkerBoxTooltipUtil.abbreviateInteger(stack.getCount());
      else
        countLabel = String.valueOf(stack.getCount());
    }

    x = this.slotXOffset + x + this.slotWidth * (slot % maxRowSize);
    y = this.slotYOffset + y + this.slotHeight * (slot / maxRowSize);

    context.drawItem(stack, x, y);
    context.drawItemInSlot(textRenderer, stack, x, y, countLabel);
  }

  protected void drawItems(int x, int y, DrawContext context, TextRenderer textRenderer) {
    if (this.previewType == PreviewType.COMPACT) {
      boolean shortItemCounts = this.config.shortItemCounts();

      for (int slot = 0, size = this.items.size(); slot < size; ++slot) {
        this.drawItem(this.items.get(slot).get(), x, y, context, textRenderer, slot, shortItemCounts);
      }
    } else {
      for (MergedItemStack compactor : this.items) {
        for (int slot = 0, size = compactor.size(); slot < size; ++slot) {
          this.drawItem(compactor.getSubStack(slot), x, y, context, textRenderer, slot, false);
        }
      }
    }
  }


  /**
   * Draw the tooltip that may be show when hovering a preview within a locked tooltip.
   */
  protected void drawInnerTooltip(int x, int y, DrawContext context, TextRenderer textRenderer, int mouseX,
      int mouseY) {
    ItemStack stack = this.getStackAt(mouseX - x, mouseY - y);

    if (!stack.isEmpty())
      context.drawItemTooltip(textRenderer, stack, mouseX, mouseY);
  }

  protected void drawSlotHighlight(int x, int y, DrawContext context, int mouseX, int mouseY) {
    int slot = this.getSlotAt(mouseX - x, mouseY - y);

    if (slot >= 0 && slot < this.getInvSize()) {
      int maxRowSize = this.getMaxRowSize();

      x = this.slotXOffset + x + this.slotWidth * (slot % maxRowSize);
      y = this.slotYOffset + y + this.slotHeight * (slot / maxRowSize);

      HandledScreen.drawSlotHighlight(context, x, y, 0);
    }
  }
}
