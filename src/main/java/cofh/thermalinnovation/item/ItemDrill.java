package cofh.thermalinnovation.item;

import cofh.core.init.CoreEnchantments;
import cofh.core.init.CoreProps;
import cofh.core.item.IAOEBreakItem;
import cofh.core.key.KeyBindingItemMultiMode;
import cofh.core.util.RayTracer;
import cofh.core.util.core.IInitializer;
import cofh.core.util.helpers.ChatHelper;
import cofh.core.util.helpers.ColorHelper;
import cofh.core.util.helpers.ItemHelper;
import cofh.core.util.helpers.StringHelper;
import cofh.thermalfoundation.init.TFProps;
import cofh.thermalfoundation.item.ItemMaterial;
import cofh.thermalinnovation.ThermalInnovation;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.ModelBakery;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Enchantments;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemSpade;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static cofh.core.util.helpers.RecipeHelper.*;

public class ItemDrill extends ItemMultiRFTool implements IInitializer, IAOEBreakItem {

	public ItemDrill() {

		super("thermalinnovation");

		setUnlocalizedName("drill");
		setCreativeTab(ThermalInnovation.tabTools);

		toolClasses.add("pickaxe");
		toolClasses.add("shovel");
		toolClasses.add("drill");

		effectiveBlocks.addAll(ItemPickaxe.EFFECTIVE_ON);
		effectiveBlocks.addAll(ItemSpade.EFFECTIVE_ON);

		effectiveMaterials.add(Material.IRON);
		effectiveMaterials.add(Material.ANVIL);
		effectiveMaterials.add(Material.ROCK);
		effectiveMaterials.add(Material.ICE);
		effectiveMaterials.add(Material.PACKED_ICE);
		effectiveMaterials.add(Material.GLASS);
		effectiveMaterials.add(Material.REDSTONE_LIGHT);

		effectiveMaterials.add(Material.GROUND);
		effectiveMaterials.add(Material.GRASS);
		effectiveMaterials.add(Material.SAND);
		effectiveMaterials.add(Material.SNOW);
		effectiveMaterials.add(Material.CRAFTED_SNOW);
		effectiveMaterials.add(Material.CLAY);
	}

	@Override
	public void addInformation(ItemStack stack, @Nullable World worldIn, List<String> tooltip, ITooltipFlag flagIn) {

		if (StringHelper.displayShiftForDetail && !StringHelper.isShiftKeyDown()) {
			tooltip.add(StringHelper.shiftForDetails());
		}
		if (!StringHelper.isShiftKeyDown()) {
			return;
		}
		tooltip.add(StringHelper.getInfoText("info.thermalinnovation.drill.a.0"));
		tooltip.add(StringHelper.localize("info.thermalinnovation.drill.c." + getMode(stack)));
		tooltip.add(StringHelper.localizeFormat("info.thermalinnovation.drill.b.0", StringHelper.getKeyName(KeyBindingItemMultiMode.INSTANCE.getKey())));

		if (isCreative(stack)) {
			tooltip.add(StringHelper.localize("info.cofh.charge") + ": 1.21G RF");
		} else {
			tooltip.add(StringHelper.localize("info.cofh.charge") + ": " + StringHelper.getScaledNumber(getEnergyStored(stack)) + " / " + StringHelper.getScaledNumber(getMaxEnergyStored(stack)) + " RF");
		}
	}

	@Override
	public void getSubItems(CreativeTabs tab, NonNullList<ItemStack> items) {

		if (enable && isInCreativeTab(tab)) {
			for (int metadata : itemList) {
				if (metadata != CREATIVE) {
					if (TFProps.showEmptyItems) {
						items.add(setDefaultTag(new ItemStack(this, 1, metadata), 0));
					}
					if (TFProps.showFullItems) {
						items.add(setDefaultTag(new ItemStack(this, 1, metadata), getBaseCapacity(metadata)));
					}
				} else {
					if (TFProps.showCreativeItems) {
						items.add(setDefaultTag(new ItemStack(this, 1, metadata), getBaseCapacity(metadata)));
					}
				}
			}
		}
	}

	@Override
	public void onUpdate(ItemStack stack, World world, Entity entity, int slot, boolean isSelected) {

		if (!isActive(stack)) {
			return;
		}
		long activeTime = stack.getTagCompound().getLong(CoreProps.ACTIVE);

		if (entity.world.getTotalWorldTime() > activeTime) {
			stack.getTagCompound().removeTag(CoreProps.ACTIVE);
		}
	}

	@Override
	public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {

		if (EnumEnchantmentType.BREAKABLE.equals(enchantment.type)) {
			return enchantment.equals(Enchantments.UNBREAKING);
		}
		return enchantment.type.canEnchantItem(this) || enchantment.canApply(new ItemStack(Items.IRON_PICKAXE)) || enchantment.canApply(new ItemStack(Items.IRON_SHOVEL));
	}

	@Override
	public boolean onBlockStartBreak(ItemStack stack, BlockPos pos, EntityPlayer player) {

		World world = player.world;
		IBlockState state = world.getBlockState(pos);

		if (state.getBlockHardness(world, pos) == 0.0F) {
			return false;
		}
		if (!canHarvestBlock(state, stack)) {
			if (!player.capabilities.isCreativeMode) {
				useEnergy(stack, 1, false);
			}
			return false;
		}
		// world.playEvent(2001, pos, Block.getStateId(state));
		if (player.isSneaking()) {
			if (!player.capabilities.isCreativeMode) {
				useEnergy(stack, 1, false);
			}
			return false;
		}
		float refStrength = state.getPlayerRelativeBlockHardness(player, world, pos);
		if (refStrength != 0.0F) {
			RayTraceResult traceResult = RayTracer.retrace(player, false);

			if (traceResult == null || traceResult.sideHit == null) {
				return false;
			}
			int count = 1;
			int mode = getMode(stack);

			switch (mode) {
				case SINGLE:
					break;
				case TUNNEL_2:
					count += breakTunnel2(player, world, pos, traceResult, refStrength);
					break;
				case AREA_3:
					count += breakArea3(player, world, pos, traceResult, refStrength);
					break;
				case CUBE_3:
					count += breakCube3(player, world, pos, traceResult, refStrength);
					break;
				case AREA_5:
					count += breakArea5(player, world, pos, traceResult, refStrength);
					break;
			}
			if (count > 0 && !player.capabilities.isCreativeMode) {
				useEnergy(stack, count, false);
			}
		}
		return false;
	}

	@Override
	public boolean onEntitySwing(EntityLivingBase entityLiving, ItemStack stack) {

		setActive(stack, entityLiving);
		return true;
	}

	@Override
	public boolean shouldCauseBlockBreakReset(ItemStack oldStack, ItemStack newStack) {

		return !oldStack.equals(newStack) && (getEnergyStored(oldStack) > 0 != getEnergyStored(newStack) > 0);
	}

	@Override
	public int getItemEnchantability(ItemStack stack) {

		return getEnchantability(stack);
	}

	@Override
	public float getDestroySpeed(ItemStack stack, IBlockState state) {

		if (getEnergyStored(stack) < energyPerUse) {
			return 1.0F;
		}
		return (effectiveMaterials.contains(state.getMaterial()) || effectiveBlocks.contains(state)) ? getEfficiency(stack) - MODE_EFF[getMode(stack)] : 1.0F;
	}

	@Override
	public Multimap<String, AttributeModifier> getAttributeModifiers(EntityEquipmentSlot slot, ItemStack stack) {

		Multimap<String, AttributeModifier> multimap = HashMultimap.create();

		if (slot == EntityEquipmentSlot.MAINHAND) {
			if (getEnergyStored(stack) >= energyPerUse) {
				multimap.put(SharedMonsterAttributes.ATTACK_SPEED.getName(), new AttributeModifier(ATTACK_SPEED_MODIFIER, "Tool modifier", -2.2F, 0));
				multimap.put(SharedMonsterAttributes.ATTACK_DAMAGE.getName(), new AttributeModifier(ATTACK_DAMAGE_MODIFIER, "Tool modifier", getAttackDamage(stack), 0));
			} else {
				multimap.put(SharedMonsterAttributes.ATTACK_SPEED.getName(), new AttributeModifier(ATTACK_SPEED_MODIFIER, "Tool modifier", -3.2F, 0));
				multimap.put(SharedMonsterAttributes.ATTACK_DAMAGE.getName(), new AttributeModifier(ATTACK_DAMAGE_MODIFIER, "Tool modifier", 0, 0));
			}
		}
		return multimap;
	}

	/* HELPERS */
	@Override
	protected int getCapacity(ItemStack stack) {

		if (!typeMap.containsKey(ItemHelper.getItemDamage(stack))) {
			return 0;
		}
		int capacity = typeMap.get(ItemHelper.getItemDamage(stack)).capacity;
		int enchant = EnchantmentHelper.getEnchantmentLevel(CoreEnchantments.holding, stack);

		return capacity + capacity * enchant / 2;
	}

	@Override
	protected int getReceive(ItemStack stack) {

		if (!typeMap.containsKey(ItemHelper.getItemDamage(stack))) {
			return 0;
		}
		return typeMap.get(ItemHelper.getItemDamage(stack)).recv;
	}

	public int getBaseCapacity(int metadata) {

		if (!typeMap.containsKey(metadata)) {
			return 0;
		}
		return typeMap.get(metadata).capacity;
	}

	public int getEnchantability(ItemStack stack) {

		if (!typeMap.containsKey(ItemHelper.getItemDamage(stack))) {
			return 0;
		}
		return typeMap.get(ItemHelper.getItemDamage(stack)).enchantability;
	}

	public int getHarvestLevel(ItemStack stack) {

		if (!typeMap.containsKey(ItemHelper.getItemDamage(stack)) || getEnergyStored(stack) <= energyPerUse) {
			return -1;
		}
		return typeMap.get(ItemHelper.getItemDamage(stack)).harvestLevel;
	}

	public float getAttackDamage(ItemStack stack) {

		if (!typeMap.containsKey(ItemHelper.getItemDamage(stack))) {
			return 0;
		}
		if (getEnergyStored(stack) < energyPerUse * 2) {
			return 0;
		}
		return typeMap.get(ItemHelper.getItemDamage(stack)).attackDamage;
	}

	public float getEfficiency(ItemStack stack) {

		if (!typeMap.containsKey(ItemHelper.getItemDamage(stack))) {
			return 0;
		}
		return typeMap.get(ItemHelper.getItemDamage(stack)).efficiency;
	}

	/* IAOEBreakItem */
	@Override
	public ImmutableList<BlockPos> getAOEBlocks(ItemStack stack, BlockPos pos, EntityPlayer player) {

		ArrayList<BlockPos> area = new ArrayList<>();
		World world = player.getEntityWorld();
		int mode = getMode(stack);

		RayTraceResult traceResult = RayTracer.retrace(player, false);
		if (traceResult == null || traceResult.sideHit == null || !canHarvestBlock(world.getBlockState(pos), stack) || player.isSneaking()) {
			return ImmutableList.copyOf(area);
		}
		switch (mode) {
			case SINGLE:
				break;
			case TUNNEL_2:
				getAOEBlocksTunnel2(stack, world, pos, traceResult, area);
				break;
			case AREA_3:
				getAOEBlocksArea3(stack, world, pos, traceResult, area);
				break;
			case CUBE_3:
				getAOEBlocksCube3(stack, world, pos, traceResult, area);
				break;
			case AREA_5:
				getAOEBlocksArea5(stack, world, pos, traceResult, area);
				break;
		}
		return ImmutableList.copyOf(area);
	}

	/* IEnchantableItem */
	@Override
	public boolean canEnchant(ItemStack stack, Enchantment enchantment) {

		return super.canEnchant(stack, enchantment) || enchantment == CoreEnchantments.insight || enchantment == CoreEnchantments.smelting;
	}

	/* IMultiModeItem */
	@Override
	public int getNumModes(ItemStack stack) {

		if (!typeMap.containsKey(ItemHelper.getItemDamage(stack))) {
			return 0;
		}
		return typeMap.get(ItemHelper.getItemDamage(stack)).numModes;
	}

	@Override
	public void onModeChange(EntityPlayer player, ItemStack stack) {

		player.world.playSound(null, player.getPosition(), SoundEvents.BLOCK_LEVER_CLICK, SoundCategory.PLAYERS, 0.6F, 1.0F - 0.1F * getMode(stack));
		ChatHelper.sendIndexedChatMessageToPlayer(player, new TextComponentTranslation("info.thermalinnovation.drill.c." + getMode(stack)));
	}

	/* IModelRegister */
	@Override
	@SideOnly (Side.CLIENT)
	public void registerModels() {

		ModelLoader.setCustomMeshDefinition(this, stack -> new ModelResourceLocation(getRegistryName(), String.format("color0=%s,state=%s,type=%s", ColorHelper.hasColor0(stack) ? 1 : 0, this.getEnergyStored(stack) > 0 ? isActive(stack) ? "active" : "charged" : "drained", typeMap.get(ItemHelper.getItemDamage(stack)).name)));

		String[] states = { "charged", "active", "drained" };

		for (Map.Entry<Integer, ItemEntry> entry : itemMap.entrySet()) {
			for (int color0 = 0; color0 < 2; color0++) {
				for (int state = 0; state < 3; state++) {
					ModelBakery.registerItemVariants(this, new ModelResourceLocation(getRegistryName(), String.format("color0=%s,state=%s,type=%s", color0, states[state], entry.getValue().name)));
				}
			}
		}
	}

	/* IInitializer */
	@Override
	public boolean preInit() {

		ForgeRegistries.ITEMS.register(setRegistryName("drill"));
		ThermalInnovation.proxy.addIModelRegister(this);

		config();

		drillBasic = addEntryItem(0, "standard0", EnumRarity.COMMON);
		drillHardened = addEntryItem(1, "standard1", EnumRarity.COMMON);
		drillReinforced = addEntryItem(2, "standard2", EnumRarity.UNCOMMON);
		drillSignalum = addEntryItem(3, "standard3", EnumRarity.UNCOMMON);
		drillResonant = addEntryItem(4, "standard4", EnumRarity.RARE);

		drillCreative = addEntryItem(CREATIVE, "creative", HARVEST_LEVEL[4], EFFICIENCY[4], ATTACK_DAMAGE[4], ENCHANTABILITY[4], CAPACITY[4], 0, NUM_MODES[4], EnumRarity.EPIC);

		return true;
	}

	@Override
	public boolean initialize() {

		if (!enable) {
			return false;
		}
		// @formatter:off
		addShapedRecipe(drillBasic,
				" X ",
				"ICI",
				"YGY",
				'C', ItemMaterial.partToolCasing,
				'G', "gearIron",
				'I', "ingotSilver",
				'X', ItemMaterial.partDrillHead,
				'Y', "ingotTin"
		);
		addShapedUpgradeRecipe(drillHardened,
				" R ",
				"IXI",
				"RGR",
				'G', "gearInvar",
				'I', "ingotInvar",
				'R', "ingotCopper",
				'X', drillBasic
		);
		addShapedUpgradeRecipe(drillReinforced,
				" R ",
				"IXI",
				"RGR",
				'G', "gearElectrum",
				'I', "ingotElectrum",
				'R', "gemDiamond",
				'X', drillHardened
		);
		addShapedUpgradeRecipe(drillSignalum,
				" R ",
				"IXI",
				"RGR",
				'G', "gearSignalum",
				'I', "ingotSignalum",
				'R', "dustPetrotheum",
				'X', drillReinforced
		);
		addShapedUpgradeRecipe(drillResonant,
				" R ",
				"IXI",
				"RGR",
				'G', "gearEnderium",
				'I', "ingotEnderium",
				'R', "ingotLumium",
				'X', drillSignalum
		);
		// @formatter:on

		addColorRecipe(drillBasic, drillBasic, "dye");
		addColorRecipe(drillHardened, drillHardened, "dye");
		addColorRecipe(drillReinforced, drillReinforced, "dye");
		addColorRecipe(drillSignalum, drillSignalum, "dye");
		addColorRecipe(drillResonant, drillResonant, "dye");

		addColorRemoveRecipe(drillBasic, drillBasic);
		addColorRemoveRecipe(drillHardened, drillHardened);
		addColorRemoveRecipe(drillReinforced, drillReinforced);
		addColorRemoveRecipe(drillSignalum, drillSignalum);
		addColorRemoveRecipe(drillResonant, drillResonant);
		return true;
	}

	private static void config() {

		String category = "Item.Drill";
		String comment;

		enable = ThermalInnovation.CONFIG.get(category, "Enable", true);

		int capacity = CAPACITY_BASE;
		comment = "Adjust this value to change the amount of Energy (in RF) stored by a Basic Fluxbore. This base value will scale with item level.";
		capacity = ThermalInnovation.CONFIG.getConfiguration().getInt("BaseCapacity", category, capacity, CAPACITY_MIN, CAPACITY_MAX, comment);

		int xfer = XFER_BASE;
		comment = "Adjust this value to change the amount of Energy (in RF/t) that can be received by a Basic Fluxbore. This base value will scale with item level.";
		xfer = ThermalInnovation.CONFIG.getConfiguration().getInt("BaseReceive", category, xfer, XFER_MIN, XFER_MAX, comment);

		for (int i = 0; i < CAPACITY.length; i++) {
			CAPACITY[i] *= capacity;
			XFER[i] *= xfer;
		}
	}

	/* ENTRY */
	public class TypeEntry {

		public final String name;

		public final int harvestLevel;
		public final float efficiency;
		public final float attackDamage;
		public final int enchantability;

		public final int capacity;
		public final int recv;
		public final int numModes;

		TypeEntry(String name, int harvestLevel, float efficiency, float attackDamage, int enchantability, int capacity, int recv, int numModes) {

			this.name = name;
			this.harvestLevel = harvestLevel;
			this.efficiency = efficiency;
			this.attackDamage = attackDamage;
			this.enchantability = enchantability;
			this.capacity = capacity;
			this.recv = recv;
			this.numModes = numModes;
		}
	}

	private void addEntry(int metadata, String name, int harvestLevel, float efficiency, float attackDamage, int enchantability, int capacity, int xfer, int numModes) {

		typeMap.put(metadata, new TypeEntry(name, harvestLevel, efficiency, attackDamage, enchantability, capacity, xfer, numModes));
	}

	private ItemStack addEntryItem(int metadata, String name, EnumRarity rarity) {

		addEntry(metadata, name, HARVEST_LEVEL[metadata], EFFICIENCY[metadata], ATTACK_DAMAGE[metadata], ENCHANTABILITY[metadata], CAPACITY[metadata], XFER[metadata], NUM_MODES[metadata]);
		return addItem(metadata, name, rarity);
	}

	private ItemStack addEntryItem(int metadata, String name, int harvestLevel, float efficiency, float attackDamage, int enchantability, int capacity, int xfer, int numModes, EnumRarity rarity) {

		addEntry(metadata, name, harvestLevel, efficiency, attackDamage, enchantability, capacity, xfer, numModes);
		return addItem(metadata, name, rarity);
	}

	private static Int2ObjectOpenHashMap<TypeEntry> typeMap = new Int2ObjectOpenHashMap<>();

	public static final int SINGLE = 0;
	public static final int TUNNEL_2 = 1;
	public static final int AREA_3 = 2;
	public static final int CUBE_3 = 3;
	public static final int AREA_5 = 4;

	public static final int[] HARVEST_LEVEL = { 2, 2, 3, 3, 4 };
	public static final float[] EFFICIENCY = { 6.0F, 7.5F, 9.0F, 10.5F, 12.0F };
	public static final float[] ATTACK_DAMAGE = { 3.0F, 3.5F, 4.0F, 4.5F, 5.0F };
	public static final int[] ENCHANTABILITY = { 10, 10, 15, 15, 20 };

	public static final int[] CAPACITY = { 1, 3, 6, 10, 15 };
	public static final int[] XFER = { 1, 4, 9, 16, 25 };
	public static final int[] NUM_MODES = { 2, 3, 3, 4, 5 };
	public static final float[] MODE_EFF = { 0, 2.0F, 4.0F, 8.0F, 8.0F };

	public static boolean enable = true;

	/* REFERENCES */
	public static ItemStack drillBasic;
	public static ItemStack drillHardened;
	public static ItemStack drillReinforced;
	public static ItemStack drillSignalum;
	public static ItemStack drillResonant;

	public static ItemStack drillCreative;

}
