package hellfirepvp.astralsorcery.common.tile;

import hellfirepvp.astralsorcery.AstralSorcery;
import hellfirepvp.astralsorcery.client.ClientScheduler;
import hellfirepvp.astralsorcery.common.constellation.CelestialHandler;
import hellfirepvp.astralsorcery.common.constellation.Constellation;
import hellfirepvp.astralsorcery.common.crafting.altar.AbstractAltarRecipe;
import hellfirepvp.astralsorcery.common.crafting.altar.ActiveCraftingTask;
import hellfirepvp.astralsorcery.common.crafting.altar.AltarRecipeRegistry;
import hellfirepvp.astralsorcery.common.starlight.transmission.ITransmissionReceiver;
import hellfirepvp.astralsorcery.common.starlight.transmission.base.SimpleTransmissionReceiver;
import hellfirepvp.astralsorcery.common.starlight.transmission.registry.TransmissionClassRegistry;
import hellfirepvp.astralsorcery.common.tile.base.TileReceiverBaseInventory;
import hellfirepvp.astralsorcery.common.util.ItemUtils;
import hellfirepvp.astralsorcery.common.util.MiscUtils;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * This class is part of the Astral Sorcery Mod
 * The complete source code for this mod can be found on github.
 * Class: TileAltar
 * Created by HellFirePvP
 * Date: 11.05.2016 / 18:18
 */
public class TileAltar extends TileReceiverBaseInventory {

    private ActiveCraftingTask craftingTask = null;

    private AltarLevel level = AltarLevel.DISCOVERY;
    private boolean doesSeeSky = false;
    private int experience = 0;
    private int starlightStored = 0;

    public TileAltar() {
        super(9);
    }

    public TileAltar(AltarLevel level) {
        super(9);
        this.level = level;
    }

    private void receiveStarlight(Constellation type, double amount) {
        if(amount <= 0.001) return;

        starlightStored = Math.min(getMaxStarlightStorage(), (int) (starlightStored + (amount * 100D)));
        markForUpdate();
    }

    @Override
    public void update() {
        super.update();

        if((ticksExisted & 15) == 0) {
            updateSkyState(worldObj.canSeeSky(getPos()));
        }

        if(!worldObj.isRemote) {
            boolean needUpdate = false;

            needUpdate = starlightPassive(needUpdate);
            needUpdate = doTryCraft(needUpdate);

            if(needUpdate) {
                markForUpdate();
            }
        } else {
            if(getActiveCraftingTask() != null) {
                doCraftEffects();
            }
        }
    }

    @SideOnly(Side.CLIENT)
    private void doCraftEffects() {
        craftingTask.getRecipeToCraft().onCraftClientTick(this, ClientScheduler.getClientTick());
    }

    private boolean doTryCraft(boolean needUpdate) {
        if(craftingTask == null) return needUpdate;
        AbstractAltarRecipe altarRecipe = craftingTask.getRecipeToCraft();
        if(!altarRecipe.matches(this)) {
            abortCrafting();
            return true;
        }
        if(craftingTask.isFinished()) {
            finishCrafting();
            findRecipe();
            return true;
        }
        craftingTask.tick(this);
        return needUpdate;
    }

    private void finishCrafting() {
        if(craftingTask == null) return; //Wtf

        AbstractAltarRecipe recipe = craftingTask.getRecipeToCraft();
        ItemStack out = recipe.getOutput(inv[4]); //Central item helps defining output - probably, eventually.
        out = ItemUtils.copyStackWithSize(out, out.stackSize);
        for (int i = 0; i < 9; i++) {
            ItemUtils.decrStackInInventory(inv, i);
        }
        for (EnumFacing dir : EnumFacing.VALUES) {
            IInventory i = MiscUtils.getTileAt(worldObj, pos.offset(dir), IInventory.class);
            if(i != null) {
                if(ItemUtils.tryPlaceItemInInventory(out, i)) {
                    if(out.stackSize == 0) {
                        break;
                    }
                }
            }
        }
        if(out.stackSize > 0) {
            ItemUtils.dropItem(worldObj, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, out);
        }

        starlightStored = Math.max(0, starlightStored - recipe.getPassiveStarlightRequired()); //Shouldn't reach < 0 but uuugh... safety
        addExpAndTryLevel((int) (recipe.getCraftExperience() * recipe.getCraftExperienceMultiplier()));

        craftingTask = null;
        markForUpdate();
    }

    private void addExpAndTryLevel(int exp) {
        if(level != AltarLevel.ENDGAME) {
            experience += exp;
            AltarLevel next = level.tryLevelUp(this);
            if(next.ordinal() >= level.ordinal()) {
                onLevelUp(level, next);
                level = next;
                experience = 0;
                markForUpdate();
            }
        } else {
            experience = Integer.MAX_VALUE;
        }
    }

    private void onLevelUp(AltarLevel level, AltarLevel next) {

    }

    private void abortCrafting() {
        this.craftingTask = null;
        markForUpdate();
    }

    private boolean starlightPassive(boolean needUpdate) {
        if(starlightStored > 0) needUpdate = true;
        starlightStored *= getAltarLevel().ordinal() != 0 ? 0.995 : 0.95;

        if(doesSeeSky()) {
            int collect = getAltarLevel().ordinal() != 0 ? 60 : getPos().getY() / 2;
            double perc =  0.2 + (0.8 * CelestialHandler.calcDaytimeDistribution(worldObj));
            starlightStored = Math.min(getMaxStarlightStorage(), (int) (starlightStored + (collect * perc)));
            return true;
        }
        return needUpdate;
    }

    @Nullable
    public ActiveCraftingTask getActiveCraftingTask() {
        return craftingTask;
    }

    public float getAmbientStarlightPercent() {
        return ((float) starlightStored) / ((float) getMaxStarlightStorage());
    }

    public int getStarlightStored() {
        return starlightStored;
    }

    public int getMaxStarlightStorage() {
        return getAltarLevel().ordinal() != 0 ? 4000 : 1000;
    }

    @Override
    protected void onInventoryChanged() {
        if(!worldObj.isRemote) {
            if(getActiveCraftingTask() != null) {
                AbstractAltarRecipe altarRecipe = craftingTask.getRecipeToCraft();
                if(!altarRecipe.matches(this)) {
                    abortCrafting();
                }
            }

            findRecipe();
        }
    }

    private void findRecipe() {
        AbstractAltarRecipe recipe = AltarRecipeRegistry.findMatchingRecipe(this);
        //System.out.println(recipe == null ? "didn't find recipe" : "found recipe");
        if(recipe != null) {
            this.craftingTask = new ActiveCraftingTask(recipe);
            markForUpdate();
        }
    }

    protected void updateSkyState(boolean seesSky) {
        boolean update = doesSeeSky != seesSky;
        this.doesSeeSky = seesSky;
        if(update) {
            markForUpdate();
        }
    }

    public boolean doesSeeSky() {
        return doesSeeSky;
    }

    public AltarLevel getAltarLevel() {
        return level;
    }

    public int getCraftingRecipeWidth() {
        return 3;
    }

    public int getCraftingRecipeHeight() {
        return 3;
    }

    @Override
    public void readCustomNBT(NBTTagCompound compound) {
        super.readCustomNBT(compound);

        this.level = AltarLevel.values()[compound.getInteger("level")];
        this.experience = compound.getInteger("exp");
        this.starlightStored = compound.getInteger("starlight");
    }

    @Override
    public void writeCustomNBT(NBTTagCompound compound) {
        super.writeCustomNBT(compound);

        compound.setInteger("level", level.ordinal());
        compound.setInteger("exp", experience);
        compound.setInteger("starlight", starlightStored);
    }

    @Nullable
    @Override
    public String getUnLocalizedDisplayName() {
        return "tile.BlockAltar.general.name";
    }

    @Override
    public ITransmissionReceiver provideEndpoint(BlockPos at) {
        return new TransmissionReceiverAltar(at);
    }

    @Override
    public String getInventoryName() {
        return getUnLocalizedDisplayName();
    }

    public static enum AltarLevel {

        DISCOVERY(100),
        ATTENUATION(1000, false),
        CONSTELLATION_CRAFT(4000),
        TRAIT_CRAFT(12000),
        ENDGAME(-1);

        private final int totalExpNeededToLevelUp;
        private boolean canLevelToByExpGain = true;

        private AltarLevel(int levelExp) {
            this.totalExpNeededToLevelUp = levelExp;
        }

        private AltarLevel(int levelExp, boolean canLevelToByExpGain) {
            this.totalExpNeededToLevelUp = levelExp;
            this.canLevelToByExpGain = canLevelToByExpGain;
        }

        public int getTotalExpNeededForLevel() {
            return totalExpNeededToLevelUp;
        }

        public boolean hasNextLevel() {
            return totalExpNeededToLevelUp > 0;
        }

        public AltarLevel tryLevelUp(TileAltar ta) {
            if(!hasNextLevel()) return this;
            int current = ta.experience;
            if(ordinal() + 1 >= values().length) return this;
            AltarLevel next = values()[ordinal() + 1];
            if(!next.canLevelToByExpGain) return this;
            if(current >= totalExpNeededToLevelUp) {
                return next;
            }
            return this;
        }

    }

    public static class TransmissionReceiverAltar extends SimpleTransmissionReceiver {

        public TransmissionReceiverAltar(@Nonnull BlockPos thisPos) {
            super(thisPos);
        }

        @Override
        public void onStarlightReceive(World world, boolean isChunkLoaded, Constellation type, double amount) {
            if(isChunkLoaded) {
                TileAltar ta = MiscUtils.getTileAt(world, getPos(), TileAltar.class);
                if(ta != null) {
                    ta.receiveStarlight(type, amount);
                }
            }
        }

        @Override
        public TransmissionClassRegistry.TransmissionProvider getProvider() {
            return new AltarReceiverProvider();
        }

    }

    public static class AltarReceiverProvider implements TransmissionClassRegistry.TransmissionProvider {

        @Override
        public TransmissionReceiverAltar provideEmptyNode() {
            return new TransmissionReceiverAltar(null);
        }

        @Override
        public String getIdentifier() {
            return AstralSorcery.MODID + ":TransmissionReceiverAltar";
        }

    }

}