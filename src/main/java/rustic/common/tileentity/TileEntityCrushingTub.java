package rustic.common.tileentity;

import java.util.Random;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBucket;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.UniversalBucket;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.TileFluidHandler;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import rustic.common.crafting.CrushingTubRecipe;
import rustic.common.crafting.ICrushingTubRecipe;
import rustic.common.crafting.Recipes;
import rustic.common.items.ModItems;

public class TileEntityCrushingTub extends TileFluidHandler {

	public static int capacity = Fluid.BUCKET_VOLUME * 8;

	public ItemStackHandler itemStackHandler = new ItemStackHandler(1) {
		@Override
		protected void onContentsChanged(int slot) {
			IBlockState state = world.getBlockState(pos);
			TileEntityCrushingTub.this.world.addBlockEvent(TileEntityCrushingTub.this.pos,
					TileEntityCrushingTub.this.getBlockType(), 1, 0);
			getWorld().notifyBlockUpdate(pos, state, state, 3);
			world.notifyNeighborsOfStateChange(TileEntityCrushingTub.this.pos,
					TileEntityCrushingTub.this.getBlockType(), true);
			TileEntityCrushingTub.this.markDirty();
		}
	};

	public TileEntityCrushingTub() {
		super();
		tank = new FluidTank(capacity) {
			@Override
			protected void onContentsChanged() {
				markDirty();
				getWorld().notifyBlockUpdate(pos, getWorld().getBlockState(pos), getWorld().getBlockState(pos), 2);
			}
		};
		tank.setTileEntity(this);
		tank.setCanFill(false);
		tank.setCanDrain(true);
	}

	public void crush(EntityLivingBase entity) {
		if (!itemStackHandler.getStackInSlot(0).isEmpty()) {
			ItemStack stack = itemStackHandler.getStackInSlot(0);
			for (ICrushingTubRecipe recipe : Recipes.crushingTubRecipes) {
				if (recipe.matches(stack)) {
					FluidStack output = recipe.getResult();
					if (this.getAmount() <= this.getCapacity() - output.amount) {
						if (!this.world.isRemote) {
							tank.fillInternal(output, true);
							itemStackHandler.extractItem(0, 1, false);
							ItemStack by = recipe.getByproduct().copy();
							if (!by.isEmpty()) {
								Block.spawnAsEntity(world, pos, by);
							}
							this.world.playSound((EntityPlayer)null, this.pos.getX() + 0.5, this.pos.getY() + 0.5, this.pos.getZ() + 0.5, SoundEvents.BLOCK_SLIME_FALL, SoundCategory.BLOCKS, 0.5F, this.world.rand.nextFloat() * 0.1F + 0.9F);
							IBlockState state = world.getBlockState(pos);
							this.world.addBlockEvent(this.pos, this.getBlockType(), 1, 0);
							getWorld().notifyBlockUpdate(pos, state, state, 3);
							this.world.notifyNeighborsOfStateChange(this.pos, this.getBlockType(), true);
							markDirty();
						} else {
							Random rand = this.world.rand;
							if (stack.getHasSubtypes()) {
			                    for (int i = 0; i < rand.nextInt(8) + 8; ++i) {
									this.world.spawnParticle(EnumParticleTypes.ITEM_CRACK, this.pos.getX() + 0.5, this.pos.getY() + 0.5, this.pos.getZ() + 0.5,
											((rand.nextDouble() * 0.1) - 0.05), ((rand.nextDouble() * 0.05) + 0.05),
											((rand.nextDouble() * 0.1) - 0.05), Item.getIdFromItem(stack.getItem()), stack.getMetadata());
								}
							} else {
			                    for (int i = 0; i < rand.nextInt(8) + 8; ++i) {
									this.world.spawnParticle(EnumParticleTypes.ITEM_CRACK, this.pos.getX() + 0.5, this.pos.getY() + 0.5, this.pos.getZ() + 0.5,
											((rand.nextDouble() * 0.2) - 0.1), ((rand.nextDouble() * 0.1) + 0.05),
											((rand.nextDouble() * 0.2) - 0.1), Item.getIdFromItem(stack.getItem()));
								}
			                }
						}
					}
				}
			}
		}
	}

	public boolean activate(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
			EnumFacing side, float hitX, float hitY, float hitZ) {
		ItemStack heldItem = player.getHeldItem(hand);
		if (heldItem != ItemStack.EMPTY) {
			if ((FluidUtil.getFluidHandler(heldItem) != null || heldItem.getItem() instanceof ItemBucket || heldItem.getItem() instanceof UniversalBucket) && FluidUtil.getFluidContained(heldItem) == null) {
				boolean didFill = FluidUtil.interactWithFluidHandler(player, hand, this.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, side));
				if (didFill) {
					//player.setHeldItem(hand, didFill.getResult());
					this.world.addBlockEvent(this.pos, this.getBlockType(), 1, 0);
					getWorld().notifyBlockUpdate(pos, state, state, 3);
					this.world.notifyNeighborsOfStateChange(this.pos, this.getBlockType(), true);
					this.markDirty();
					return true;
				}
			} else {
				player.setHeldItem(hand, this.itemStackHandler.insertItem(0, heldItem, false));
				this.world.addBlockEvent(this.pos, this.getBlockType(), 1, 0);
				getWorld().notifyBlockUpdate(pos, state, state, 3);
				this.world.notifyNeighborsOfStateChange(this.pos, this.getBlockType(), true);
				markDirty();
				return true;
			}
		} else if (player.isSneaking() && this.getAmount() > 0) {
			FluidStack drained = this.tank.drainInternal(capacity, true);
			SoundEvent soundevent = drained.getFluid().getEmptySound(drained);
			this.world.playSound(null, this.pos, soundevent, SoundCategory.BLOCKS, 1F, 1F);
			this.world.addBlockEvent(this.pos, this.getBlockType(), 1, 0);
			getWorld().notifyBlockUpdate(pos, state, state, 3);
			this.world.notifyNeighborsOfStateChange(this.pos, this.getBlockType(), true);
			markDirty();
			return true;
		}
		if (itemStackHandler.getStackInSlot(0) != ItemStack.EMPTY && !world.isRemote) {
			world.spawnEntity(
					new EntityItem(world, player.posX, player.posY, player.posZ, itemStackHandler.getStackInSlot(0)));
			itemStackHandler.setStackInSlot(0, ItemStack.EMPTY);
			this.world.addBlockEvent(this.pos, this.getBlockType(), 1, 0);
			getWorld().notifyBlockUpdate(pos, state, state, 3);
			this.world.notifyNeighborsOfStateChange(this.pos, this.getBlockType(), true);
			markDirty();
			return true;
		}
		return false;
	}

	public int getCapacity() {
		return tank.getCapacity();
	}

	public int getAmount() {
		return tank.getFluidAmount();
	}

	public FluidTank getTank() {
		return tank;
	}

	public Fluid getFluid() {
		if (tank.getFluid() != null) {
			return tank.getFluid().getFluid();
		}
		return null;
	}

	@Override
	public boolean hasCapability(@Nonnull Capability<?> capability, @Nullable EnumFacing facing) {
		return capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY
				|| capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY
				|| super.hasCapability(capability, facing);
	}

	@SuppressWarnings("unchecked")
	@Override
	@Nullable
	public <T> T getCapability(@Nonnull Capability<T> capability, @Nullable EnumFacing facing) {
		if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
			return (T) tank;
		} else if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
			return (T) itemStackHandler;
		}
		return super.getCapability(capability, facing);
	}

	@Override
	public NBTTagCompound getUpdateTag() {
		return writeToNBT(new NBTTagCompound());
	}

	@Nullable
	@Override
	public SPacketUpdateTileEntity getUpdatePacket() {
		return new SPacketUpdateTileEntity(getPos(), 0, getUpdateTag());
	}

	@Override
	public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
		readFromNBT(pkt.getNbtCompound());
	}

	@Override
	public void readFromNBT(NBTTagCompound tag) {
		super.readFromNBT(tag);
		if (tag.hasKey("items")) {
			itemStackHandler.deserializeNBT((NBTTagCompound) tag.getTag("items"));
		}
	}

	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound tag) {
		tag = super.writeToNBT(tag);
		tag.setTag("items", itemStackHandler.serializeNBT());
		return tag;
	}

	@Override
	public boolean receiveClientEvent(int id, int type) {
		if (id == 1) {
			getWorld().notifyBlockUpdate(pos, getWorld().getBlockState(pos), getWorld().getBlockState(pos), 3);
			this.world.notifyNeighborsOfStateChange(this.pos, this.getBlockType(), true);
			this.markDirty();
			return true;
		} else {
			return super.receiveClientEvent(id, type);
		}
	}

	public void breakBlock(World world, BlockPos pos, IBlockState state) {
		this.invalidate();
		if (itemStackHandler != null && !world.isRemote) {
			for (int i = 0; i < itemStackHandler.getSlots(); i++) {
				if (itemStackHandler.getStackInSlot(i) != null) {
					Block.spawnAsEntity(world, pos, itemStackHandler.getStackInSlot(i));
				}
			}
		}
		world.setTileEntity(pos, null);
	}

}
