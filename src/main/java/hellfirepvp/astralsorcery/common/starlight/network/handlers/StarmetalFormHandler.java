package hellfirepvp.astralsorcery.common.starlight.network.handlers;

import hellfirepvp.astralsorcery.common.block.BlockCustomOre;
import hellfirepvp.astralsorcery.common.constellation.Constellation;
import hellfirepvp.astralsorcery.common.lib.BlocksAS;
import hellfirepvp.astralsorcery.common.lib.Constellations;
import hellfirepvp.astralsorcery.common.starlight.network.StarlightNetworkRegistry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * This class is part of the Astral Sorcery Mod
 * The complete source code for this mod can be found on github.
 * Class: StarmetalFormHandler
 * Created by HellFirePvP
 * Date: 05.10.2016 / 19:25
 */
public class StarmetalFormHandler implements StarlightNetworkRegistry.IStarlightBlockHandler {

    private static Map<BlockPos, IronOreReceiverNode> turningIrons = new HashMap<>();

    @Override
    public void receiveStarlight(World world, Random rand, BlockPos pos, Constellation starlightType, double amount) {
        long ms = System.currentTimeMillis();
        if(!turningIrons.containsKey(pos)) {
            IronOreReceiverNode node = new IronOreReceiverNode();
            turningIrons.put(pos, node);
            node.lastMSrec = ms;
            node.accCharge = 0D;
        }
        IronOreReceiverNode node = turningIrons.get(pos);
        long diff = ms - node.lastMSrec;
        if(diff >= 30_000) node.accCharge = 0;
        node.accCharge += amount;
        if(starlightType == Constellations.mineralis) {
            node.accCharge += amount * 6;
        }
        if(starlightType == Constellations.horologium) {
            node.accCharge += amount * 2;
        }
        node.lastMSrec = ms;

        if(node.accCharge >= 30_000) {
            turningIrons.remove(pos);

            world.setBlockState(pos, BlocksAS.customOre.getDefaultState().withProperty(BlockCustomOre.ORE_TYPE, BlockCustomOre.OreType.STARMETAL));
        }
    }

    public static class IronOreReceiverNode {

        private long lastMSrec;
        private double accCharge;

    }

}
