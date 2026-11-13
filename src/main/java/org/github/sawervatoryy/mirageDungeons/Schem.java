package org.github.sawervatoryy.mirageDungeons;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.world.block.BlockState;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Схематика, прочитанная через WorldEdit (.schem, .schematic — любые форматы WorldEdit).
 * Хранится в виде палитры строк блоков и массива индексов (порядок: x, затем z, затем y).
 */
public final class Schem {

    public final int width;
    public final int height;
    public final int length;
    public final int offX;
    public final int offY;
    public final int offZ;
    public final String[] palette;
    public final int[] blocks;

    private Schem(int w, int h, int l, int ox, int oy, int oz, String[] palette, int[] blocks) {
        this.width = w;
        this.height = h;
        this.length = l;
        this.offX = ox;
        this.offY = oy;
        this.offZ = oz;
        this.palette = palette;
        this.blocks = blocks;
    }

    public static Schem load(File file) throws IOException {
        ClipboardFormat format = ClipboardFormats.findByFile(file);
        if (format == null) {
            throw new IOException("WorldEdit не распознал формат файла " + file.getName());
        }
        Clipboard clipboard;
        try (ClipboardReader reader = format.getReader(new FileInputStream(file))) {
            clipboard = reader.read();
        }
        Region region = clipboard.getRegion();
        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 origin = clipboard.getOrigin();

        int w = region.getWidth();
        int h = region.getHeight();
        int l = region.getLength();

        Map<BlockState, Integer> index = new HashMap<>();
        List<String> pal = new ArrayList<>();
        int[] blocks = new int[w * h * l];
        int i = 0;
        for (int y = 0; y < h; y++) {
            for (int z = 0; z < l; z++) {
                for (int x = 0; x < w; x++) {
                    BlockState state = clipboard.getBlock(
                            BlockVector3.at(min.x() + x, min.y() + y, min.z() + z));
                    Integer id = index.get(state);
                    if (id == null) {
                        id = pal.size();
                        index.put(state, id);
                        pal.add(BukkitAdapter.adapt(state).getAsString());
                    }
                    blocks[i++] = id;
                }
            }
        }
        return new Schem(w, h, l,
                min.x() - origin.x(),
                min.y() - origin.y(),
                min.z() - origin.z(),
                pal.toArray(new String[0]), blocks);
    }
}
