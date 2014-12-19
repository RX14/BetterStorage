package net.mcft.copy.betterstorage.proxy;

import java.util.Map.Entry;
import java.util.Set;

import net.mcft.copy.betterstorage.BetterStorage;
import net.mcft.copy.betterstorage.api.stand.BetterStorageArmorStand;
import net.mcft.copy.betterstorage.api.stand.EnumArmorStandRegion;
import net.mcft.copy.betterstorage.attachment.EnumAttachmentInteraction;
import net.mcft.copy.betterstorage.attachment.IHasAttachments;
import net.mcft.copy.betterstorage.config.GlobalConfig;
import net.mcft.copy.betterstorage.content.BetterStorageItems;
import net.mcft.copy.betterstorage.content.BetterStorageTiles;
import net.mcft.copy.betterstorage.entity.EntityCluckington;
import net.mcft.copy.betterstorage.item.ItemBackpack;
import net.mcft.copy.betterstorage.item.ItemBucketSlime;
import net.mcft.copy.betterstorage.item.cardboard.ICardboardItem;
import net.mcft.copy.betterstorage.item.cardboard.ItemCardboardSheet;
import net.mcft.copy.betterstorage.misc.ChristmasEventHandler;
import net.mcft.copy.betterstorage.misc.EquipmentSlot;
import net.mcft.copy.betterstorage.misc.SetBlockFlag;
import net.mcft.copy.betterstorage.misc.handlers.BackpackHandler;
import net.mcft.copy.betterstorage.misc.handlers.CraftingHandler;
import net.mcft.copy.betterstorage.tile.crate.CratePileCollection;
import net.mcft.copy.betterstorage.tile.entity.TileEntityLockableDoor;
import net.mcft.copy.betterstorage.tile.stand.VanillaArmorStandEquipHandler;
import net.mcft.copy.betterstorage.utils.StackUtils;
import net.mcft.copy.betterstorage.utils.WorldUtils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.BlockDoor.EnumHingePosition;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.passive.EntityChicken;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.EntityInteractEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.BreakSpeed;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.Action;
import net.minecraftforge.event.world.WorldEvent.Unload;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.Event.Result;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import net.minecraftforge.fml.common.gameevent.TickEvent.PlayerTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.WorldTickEvent;
import net.minecraftforge.fml.relauncher.Side;

public class CommonProxy {
	
	private boolean preventSlimeBucketUse = false;
	
	public void initialize() {
		
		MinecraftForge.EVENT_BUS.register(this);
		FMLCommonHandler.instance().bus().register(this);
		
		new BackpackHandler();
		new CraftingHandler();
		
		if (BetterStorage.globalConfig.getBoolean(GlobalConfig.enableChristmasEvent))
			new ChristmasEventHandler();
		
		registerArmorStandHandlers();
	}
	
	protected void registerArmorStandHandlers() {
		
		BetterStorageArmorStand.helmet     = new VanillaArmorStandEquipHandler(EnumArmorStandRegion.HEAD);
		BetterStorageArmorStand.chestplate = new VanillaArmorStandEquipHandler(EnumArmorStandRegion.CHEST);
		BetterStorageArmorStand.leggins    = new VanillaArmorStandEquipHandler(EnumArmorStandRegion.LEGS);
		BetterStorageArmorStand.boots      = new VanillaArmorStandEquipHandler(EnumArmorStandRegion.FEET);
		
		BetterStorageArmorStand.registerEquipHandler(BetterStorageArmorStand.helmet);
		BetterStorageArmorStand.registerEquipHandler(BetterStorageArmorStand.chestplate);
		BetterStorageArmorStand.registerEquipHandler(BetterStorageArmorStand.leggins);
		BetterStorageArmorStand.registerEquipHandler(BetterStorageArmorStand.boots);
		
	}
	
	@SubscribeEvent
	public void onWorldUnload(Unload event) {
		CratePileCollection.unload(event.world);
	}
	
	@SubscribeEvent
	public void onPlayerInteract(PlayerInteractEvent event) {
		
		//TODO (1.8): More severe changes.
		World world = event.entity.worldObj;
		
		EntityPlayer player = event.entityPlayer;
		ItemStack holding = player.getCurrentEquippedItem();
		Block block = world.getBlockState(event.pos).getBlock();
		boolean leftClick = (event.action == Action.LEFT_CLICK_BLOCK);
		boolean rightClick = (event.action == Action.RIGHT_CLICK_BLOCK);
		
		// Interact with attachments.
		if (leftClick || rightClick) {
			IHasAttachments hasAttachments =
					WorldUtils.get(world, event.pos, IHasAttachments.class);
			if (hasAttachments != null) {
				EnumAttachmentInteraction interactionType =
						((event.action == Action.LEFT_CLICK_BLOCK)
								? EnumAttachmentInteraction.attack
								: EnumAttachmentInteraction.use);
				if (hasAttachments.getAttachments().interact(WorldUtils.rayTrace(player, 1.0F),
				                                             player, interactionType)) {
					event.useBlock = Result.DENY;
					event.useItem = Result.DENY;
				}
			}
		}
		
		// Use cauldron to remove color from dyable items
		//TODO (1.8): Metadata is evil!
		/*if (rightClick && (block == Blocks.cauldron)) {
			int metadata = world.getBlockMetadata(event.pos);
			if (metadata > 0) {
				IDyeableItem dyeable = (((holding != null) && (holding.getItem() instanceof IDyeableItem))
						? (IDyeableItem)holding.getItem() : null);
				if ((dyeable != null) && (dyeable.canDye(holding))) {
					StackUtils.remove(holding, "display", "color");
					world.setBlockMetadataWithNotify(x, y, z, metadata - 1, 2);
					world.func_147453_f(x, y, z, block);
					
					event.useBlock = Result.DENY;
					event.useItem = Result.DENY;
				}
			}
		}*/
		
		// Prevent players from breaking blocks with broken cardboard items.
		if (leftClick && (holding != null) &&
		    (holding.getItem() instanceof ICardboardItem) &&
		    !ItemCardboardSheet.isEffective(holding))
			event.useItem = Result.DENY;
		
		// Attach locks to iron doors.
		if (!world.isRemote && BetterStorageTiles.lockableDoor != null && rightClick && block == Blocks.iron_door) {
			MovingObjectPosition target = WorldUtils.rayTrace(player, 1F);		
			if(target != null && getIronDoorHightlightBox(player, world, event.pos, target.hitVec, block) != null) {
				
				IBlockState stateLower = world.getBlockState(event.pos);
				IBlockState stateUpper = world.getBlockState(event.pos.offsetUp());
				IBlockState stateLowerLockable = BetterStorageTiles.lockableDoor.getDefaultState();
				IBlockState stateUpperLockable = BetterStorageTiles.lockableDoor.getDefaultState();
				
				for (Entry entry : (Set<Entry>) stateLower.getProperties().entrySet())
					stateLowerLockable = stateLowerLockable.withProperty((IProperty) entry.getKey(), (Comparable) entry.getValue());
				for (Entry entry : (Set<Entry>) stateUpper.getProperties().entrySet())
					stateUpperLockable = stateUpperLockable.withProperty((IProperty) entry.getKey(), (Comparable) entry.getValue());

				world.setBlockState(event.pos, stateLowerLockable, SetBlockFlag.SEND_TO_CLIENT);
				world.setBlockState(event.pos.offsetUp(), stateUpperLockable, SetBlockFlag.SEND_TO_CLIENT);

				TileEntityLockableDoor te = WorldUtils.get(world, event.pos, TileEntityLockableDoor.class);
				te.setLock(holding);
				
				player.inventory.setInventorySlotContents(player.inventory.currentItem, null);
			}
		}
		
		// Prevent eating of slime buckets after capturing them.
		if (preventSlimeBucketUse) {
			event.setCanceled(true);
			preventSlimeBucketUse = false;
		}
		
	}
	
	protected AxisAlignedBB getIronDoorHightlightBox(EntityPlayer player, World world, BlockPos pos, Vec3 hitVec, Block block) {
		//TODO (1.8): This was really ugly in the first place, might have a better method now.
		if(!StackUtils.isLock(player.getCurrentEquippedItem())) return null;
		
		IBlockState state = world.getBlockState(pos);
		boolean isMirrored = ((EnumHingePosition) state.getValue(BlockDoor.HINGEPOSITION_PROP)) == EnumHingePosition.RIGHT;
		boolean isOpen = (Boolean) state.getValue(BlockDoor.OPEN_PROP);
		EnumFacing facing = (EnumFacing) state.getValue(BlockDoor.FACING_PROP);

		AxisAlignedBB box;
		int x = pos.getX();
		int y = pos.getY();
		int z = pos.getZ();
		
		switch(facing) {
		case NORTH :
			if(!isOpen) box = AxisAlignedBB.fromBounds(x - 0.005 / 16F, y + 14.5 / 16F, z + 10 / 16F, x + 3.005 / 16F, y + 20.5 / 16F, z + 15 / 16F);
			else box = AxisAlignedBB.fromBounds(x + 10 / 16F, y + 14.5 / 16F, z - 0.005 / 16F, x + 15 / 16F, y + 20.5 / 16F, z + 3.005 / 16F);
			break;
		case EAST :
			if(!isOpen) box = AxisAlignedBB.fromBounds(x + 1 / 16F, y + 14.5 / 16F, z - 0.005 / 16F, x + 6 / 16F, y + 20.5 / 16F, z + 3.005 / 16F);
			else box = AxisAlignedBB.fromBounds(x + 12.995 / 16F, y + 14.5 / 16F, z + 10 / 16F, x + 16.005 / 16F, y + 20.5 / 16F, z + 15 / 16F);
			break;
		case WEST :
			if(!isOpen) box = AxisAlignedBB.fromBounds(x + 12.995 / 16F, y + 14.5 / 16F, z + 1 / 16F, x + 16.005 / 16F, y + 20.5 / 16F, z + 6 / 16F);
			else box = AxisAlignedBB.fromBounds(x + 1 / 16F, y + 14.5 / 16F, z + 12.995 / 16F, x + 6 / 16F, y + 20.5 / 16F, z + 16.005 / 16F);
			break;
		default :
			if(!isOpen) box = AxisAlignedBB.fromBounds(x + 10 / 16F, y + 14.5 / 16F, z + 12.995 / 16F, x + 15 / 16F, y + 20.5 / 16F, z + 16.005 / 16F);
			else box = AxisAlignedBB.fromBounds(x - 0.005 / 16F, y + 14.5 / 16F, z + 1 / 16F, x + 3.005 / 16F, y + 20.5 / 16F, z + 6 / 16F);
			break;
		}
		
		return box.isVecInside(hitVec) ? box : null;
	}
	
	@SubscribeEvent
	public void onBreakSpeed(BreakSpeed event) {
		// Stupid Forge not firing PlayerInteractEvent for left-clicks!
		// This is a workaround to instead make blocks appear unbreakable.
		EntityPlayer player = event.entityPlayer;
		ItemStack holding = player.getCurrentEquippedItem();
		if ((holding != null) && (holding.getItem() instanceof ICardboardItem) &&
		    !ItemCardboardSheet.isEffective(holding))
			event.newSpeed = -1;
	}
	
	@SubscribeEvent
	public void onEntityInteract(EntityInteractEvent event) {
		
		if (event.entity.worldObj.isRemote || event.isCanceled()) return;
		
		EntityPlayer player = event.entityPlayer;
		Entity target = event.target;
		ItemStack holding = player.getCurrentEquippedItem();
		
		if ((target.getClass() == EntityChicken.class) &&
		    (holding != null) && (holding.getItem() == Items.name_tag)) {
			
			EntityChicken chicken = (EntityChicken)target;
			if (!chicken.isDead && !chicken.isChild() &&
			    "Cluckington".equals(holding.getDisplayName()))
				EntityCluckington.spawn(chicken);
			
		}
		
		if ((BetterStorageItems.slimeBucket != null) && (target instanceof EntityLiving) &&
		    (holding != null) && (holding.getItem() == Items.bucket)) {
			ItemBucketSlime.pickUpSlime(player, (EntityLiving)target);
			if (player.getCurrentEquippedItem().getItem() instanceof ItemBucketSlime)
				preventSlimeBucketUse = true;
		}
		
	}
	
	@SubscribeEvent
	public void onWorldTick(WorldTickEvent event) {
		if (event.side == Side.SERVER)
			CratePileCollection.getCollection(event.world).onTick();
	}
	
	@SubscribeEvent
	public void onPlayerTick(PlayerTickEvent event) {
		if (event.side == Side.SERVER && event.phase == Phase.END) {
			//Cleanup in case the backpack is not equipped correctly, due to changing the backpackChestplate setting.
			ItemStack stack = event.player.getEquipmentInSlot(EquipmentSlot.CHEST);
			if(stack != null && stack.getItem() instanceof ItemBackpack && !BetterStorage.globalConfig.getBoolean(GlobalConfig.backpackChestplate)) {
				//First thing that never should happen...
				event.player.setCurrentItemOrArmor(EquipmentSlot.CHEST, null);
				ItemBackpack.setBackpack(event.player, stack, ItemBackpack.getBackpackData(event.player).contents);
			} else if((stack == null || (stack.getItem() != null && !(stack.getItem() instanceof ItemBackpack))) && ItemBackpack.getBackpackData(event.player).backpack != null && BetterStorage.globalConfig.getBoolean(GlobalConfig.backpackChestplate)) {
				//And that.
				ItemStack backpack = ItemBackpack.getBackpack(event.player);
				//Not really a good practice, I'd say.
				ItemBackpack.getBackpackData(event.player).backpack = null;
				if(stack != null) {
					//Drop the armor if the player had some and decided to switch the setting anyways.
					WorldUtils.dropStackFromEntity(event.player, stack, 4.0F);
				}
				event.player.setCurrentItemOrArmor(EquipmentSlot.CHEST, backpack);
			}
		}
	}
	
	/**
	 * Register an item model
	 * @param item item to register
	 * @param id metadata
	 * @param name model name
	 */
	public void registerItemRender(Item item, int id, String name){
		this.registerItemRender(item, id, name, "inventory");
	}
	
	/**
	 * Register an item model
	 * @param item item to register
	 * @param id metadata
	 * @param name model name
	 * @param type
	 */
	public void registerItemRender(Item item, int id, String name, String type){
		
	}
}
