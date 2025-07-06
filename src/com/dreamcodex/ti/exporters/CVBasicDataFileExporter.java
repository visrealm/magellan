package com.dreamcodex.ti.exporters;

import com.dreamcodex.ti.component.MagellanExportDialog;
import com.dreamcodex.ti.component.MapCanvas;
import com.dreamcodex.ti.component.MapEditor;
import com.dreamcodex.ti.util.*;

import java.awt.*;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.List;

import static com.dreamcodex.ti.Magellan.*;
import static com.dreamcodex.ti.util.ColorMode.*;

public class CVBasicDataFileExporter extends Exporter {

    public CVBasicDataFileExporter(MapEditor mapEditor, DataSet dataSet, Preferences preferences) {
        super(mapEditor, dataSet, preferences);
    }

    public void writeCVBasicDataFile(File mapDataFile, Preferences preferences) throws Exception{
        writeCVBasicDataFile(mapDataFile, preferences.getDefStartChar(), preferences.getDefEndChar(), preferences.getDefStartSprite(), preferences.getDefEndSprite(), preferences.getCompression(), preferences.isExportComments(), preferences.isCurrentMapOnly(), preferences.isIncludeCharNumbers(), preferences.isIncludeCharData(), preferences.isIncludeSpriteData(), preferences.isIncludeColorData(), preferences.isIncludeMapData());
    }

    public void writeCVBasicDataFile(File mapDataFile, int startChar, int endChar, int startSprite, int endSprite, int compression, boolean includeComments, boolean currMapOnly, boolean includeCharNumbers, boolean includeCharData, boolean includeSpriteData, boolean includeColorData, boolean includeMapData) throws Exception {
        if ((compression == MagellanExportDialog.COMPRESSION_RLE_BYTE || compression == MagellanExportDialog.COMPRESSION_RLE_WORD) && endChar > 127) {
            throw new Exception("RLE Compression not supported for characters > 127.");
        }
        mapEditor.storeCurrentMap();
        Map<ECMPalette, Integer> paletteMap = buildECMPaletteMap();
        BufferedWriter bw = new BufferedWriter(new FileWriter(mapDataFile));
        StringBuilder sbLine = new StringBuilder();
        if (includeColorData) {
            writeColors(startChar, endChar, includeComments, includeCharNumbers, paletteMap, sbLine, bw);
        }
        if (includeCharData) {
            writeCharacterPatterns(startChar, endChar, includeComments, includeCharNumbers, sbLine, bw);
        }
        if (includeSpriteData) {
            writeSpriteData(startSprite, endSprite, includeComments, includeSpriteData, sbLine, bw);
        }
        if (includeMapData) {
            writeMapData(compression, includeComments, currMapOnly, includeSpriteData, paletteMap, sbLine, bw);
        }
        bw.flush();
        bw.close();
    }

    private Map<ECMPalette, Integer> buildECMPaletteMap() {
        Map<ECMPalette, Integer> paletteMap = new HashMap<>(16);
        {
            int n = 0;
            for (ECMPalette ecmPalette : ecmPalettes) {
                paletteMap.put(ecmPalette, n++);
            }
        }
        return paletteMap;
    }

    private void printLine(BufferedWriter bw, String line) throws IOException
    {
        printLine(bw, line, true);
    }
    private void printLine(BufferedWriter bw, String line, String comments) throws IOException
    {
        if (comments == null || comments.isEmpty())
            printLine(bw, line, true);
        else
            printLine(bw, line + " ' " + comments, true);
    }
    private void printLine(BufferedWriter bw, String line, boolean isCommand) throws IOException
    {
        if (!isCommand) bw.write("' ");
        bw.write(line);
        bw.newLine();        
    }

    private void writeColors(int startChar, int endChar, boolean includeComments, boolean includeCharNumbers, Map<ECMPalette, Integer> paletteMap, StringBuilder sbLine, BufferedWriter bw) throws IOException {
        if (includeComments) {
            printLine(bw, "****************************************", false);
            printLine(bw, "* Colorset Definitions", false);
            printLine(bw, "****************************************", false);
        }
        if (colorMode == COLOR_MODE_GRAPHICS_1) {
            int itemCount = 0;
            printLine(bw, "CLRNUM:");
            printLine(bw, "DATA BYTE " + (clrSets.length));
            for (int i = 0; i < clrSets.length; i++) {
                if (itemCount == 0) {
                    if (i == 0) {
                        sbLine.append("CLRSET BYTE ");
                    }
                    else {
                        sbLine.append("       BYTE ");
                    }
                }
                if (itemCount > 0) {
                    sbLine.append(",");
                }
                sbLine.append("$");
                sbLine.append(Integer.toHexString(clrSets[i][Globals.INDEX_CLR_FORE]).toUpperCase());
                sbLine.append(Integer.toHexString(clrSets[i][Globals.INDEX_CLR_BACK]).toUpperCase());
                itemCount++;
                if (itemCount > 3) {
                    printLine(bw, sbLine.toString(), includeComments);
                    sbLine.delete(0, sbLine.length());
                    itemCount = 0;
                }
            }
        }
        else if (colorMode == COLOR_MODE_BITMAP) {
            sbLine.delete(0, sbLine.length());
            for (int i = startChar; i <= endChar; i++) {
                int[][] charColors = this.charColors.get(i);
                if (charColors != null) {
                    if (includeCharNumbers) {
                        printLine(bw, "CCH" + i + ":");
                        printLine(bw, "  DATA BYTE " + i);
                    }
                    sbLine.append("COL").append(i).append(":\n  DATA BYTE ");
                    for (int row = 0; row < 8; row += 2) {
                        sbLine.append("$");
                        int[] rowColors = charColors[row];
                        sbLine.append(Integer.toHexString(rowColors[1]).toUpperCase());
                        sbLine.append(Integer.toHexString(rowColors[0]).toUpperCase());
                        rowColors = charColors[row + 1];
                        sbLine.append(", $");
                        sbLine.append(Integer.toHexString(rowColors[1]).toUpperCase());
                        sbLine.append(Integer.toHexString(rowColors[0]).toUpperCase());
                        if (row < 6) {
                            sbLine.append(", ");
                        }
                    }
                    printLine(bw, sbLine.toString());
                    sbLine.delete(0, sbLine.length());
                }
            }
        }
        else if (colorMode == COLOR_MODE_ECM_2 || colorMode == COLOR_MODE_ECM_3) {
            sbLine.delete(0, sbLine.length());
            for (int i = 0; i < ecmPalettes.length; i++) {
                ECMPalette ecmPalette = ecmPalettes[i];
                for (int l = 0; l < ecmPalette.getSize() / 4; l++) {
                    if (l == 0) {
                        sbLine.append("PAL").append(i).append(":\n  DATA BYTE ");
                    }
                    else {
                        sbLine.append("  DATA BYTE ");
                    }
                    for (int c = 0; c < 4; c++) {
                        Color color = ecmPalette.getColor(c + l * 4);
                        sbLine.append("$0");
                        sbLine.append(Integer.toHexString((int) Math.round((double) color.getRed() / 17)).toUpperCase());
                        sbLine.append(Integer.toHexString((int) Math.round((double) color.getGreen() / 17)).toUpperCase());
                        sbLine.append(Integer.toHexString((int) Math.round((double) color.getBlue() / 17)).toUpperCase());
                        if (c < 3) {
                            sbLine.append(",");
                        }
                    }
                    printLine(bw, sbLine.toString(), includeComments);
                    sbLine.delete(0, sbLine.length());
                }
            }
            if (includeComments) {
                printLine(bw, "****************************************", false);
                printLine(bw, "* Tile Attributes", false);
                printLine(bw, "****************************************", false);
            }
            sbLine.delete(0, sbLine.length());
            for (int i = startChar; i <= endChar; i++) {
                sbLine.append("TAT").append(i).append(":\n  ");
                sbLine.append("  DATA BYTE $").append(Globals.toHexString((paletteMap.get(ecmCharPalettes[i]) << (colorMode == COLOR_MODE_ECM_3 ? 1 : 0)) | (ecmCharTransparency[i] ? 0x10 : 0), 2));
                printLine(bw, sbLine.toString(), includeComments);
                sbLine.delete(0, sbLine.length());
            }
        }
        if (sbLine.length() > 0) {
            printLine(bw, sbLine.toString(), includeComments);
            sbLine.delete(0, sbLine.length());
        }
    }

    private void writeCharacterPatterns(int startChar, int endChar, boolean includeComments, boolean includeCharNumbers, StringBuilder sbLine, BufferedWriter bw) throws IOException {
        if (includeComments) {
            printLine(bw, "****************************************", false);
            printLine(bw, "* Character Patterns" + (colorMode == COLOR_MODE_ECM_2 || colorMode == COLOR_MODE_ECM_3 ? " Plane 0" : ""), false);
            printLine(bw, "****************************************", false);
        }
        sbLine.delete(0, sbLine.length());
        for (int i = startChar; i <= endChar; i++) {
            String hexstr = Globals.BLANKCHAR;
            if (charGrids.get(i) != null) {
                hexstr = Globals.getHexString(charGrids.get(i)).toUpperCase();
            }
            if (includeCharNumbers) {
                printLine(bw, "PCH" + i + ":\n  DATA BYTE " + i, includeComments);
            }
            sbLine.append(colorMode == COLOR_MODE_GRAPHICS_1 || colorMode == COLOR_MODE_BITMAP ? "PAT" : "P0_").append(i).append(":\n  DATA BYTE ");
                for (int pos = 0; pos < 16; pos += 2) {
                    sbLine.append("$").append(hexstr, pos, pos + 2);
                    if (pos < 14) sbLine.append(", ");
                }
            printLine(bw, sbLine.toString(), includeComments);
            sbLine.delete(0, sbLine.length());
        }
        if (colorMode == COLOR_MODE_ECM_2 || colorMode == COLOR_MODE_ECM_3) {
            if (includeComments) {
                printLine(bw, "****************************************", false);
                printLine(bw, "* Character Patterns Plane 1", false);
                printLine(bw, "****************************************", false);
            }
            for (int i = startChar; i <= endChar; i++) {
                String hexstr = Globals.BLANKCHAR;
                if (charGrids.get(i) != null) {
                    hexstr = Globals.getHexString(charGrids.get(i), 2).toUpperCase();
                }
                sbLine.append("P1_").append(i).append(":\n  DATA BYTE ");
                for (int pos = 0; pos < 16; pos += 2) {
                    sbLine.append("$").append(hexstr, pos, pos + 2);
                    if (pos < 14) sbLine.append(", ");
                }
                printLine(bw, sbLine.toString(), includeComments);
                sbLine.delete(0, sbLine.length());
            }
        }
        if (colorMode == COLOR_MODE_ECM_3) {
            if (includeComments) {
                printLine(bw, "****************************************", false);
                printLine(bw, "* Character Patterns Plane 2", false);
                printLine(bw, "****************************************", false);
            }
            for (int i = startChar; i <= endChar; i++) {
                String hexstr = Globals.BLANKCHAR;
                if (charGrids.get(i) != null) {
                    hexstr = Globals.getHexString(charGrids.get(i), 4).toUpperCase();
                }
                sbLine.append("P2_").append(i).append(":\n  DATA BYTE ");
                for (int pos = 0; pos < 16; pos += 2) {
                    sbLine.append("$").append(hexstr, pos, pos + 2);
                    if (pos < 14) sbLine.append(", ");
                }
                printLine(bw, sbLine.toString(), includeComments);
                sbLine.delete(0, sbLine.length());
            }
        }
    }

    private void writeSpriteData(int startSprite, int endSprite, boolean includeComments, boolean includeSpriteData, StringBuilder sbLine, BufferedWriter bw) throws IOException {
        if (includeComments) {
            printLine(bw, "****************************************", false);
            printLine(bw, "* Sprite Patterns" + (colorMode == COLOR_MODE_ECM_2 || colorMode == COLOR_MODE_ECM_3 ? " Plane 0" : ""), false);
            printLine(bw, "****************************************", false);
        }
        sbLine.delete(0, sbLine.length());
        for (int i = startSprite; i <= endSprite; i++) {
            if (spriteGrids.get(i) != null) {
                String hexstr = Globals.getSpriteHexString(spriteGrids.get(i)).toUpperCase();
                sbLine.append(colorMode == COLOR_MODE_GRAPHICS_1 || colorMode == COLOR_MODE_BITMAP ? "SPR" : "S0_").append(i).append(":\n  DATA BYTE ");
                for (int pos = 0; pos < 64; pos += 2) {
                    if (pos > 0 && pos % 16 == 0) {
                        sbLine.append("  DATA BYTE ");
                    }
                    sbLine.append("$").append(hexstr, pos, pos + 2).append(pos % 16 != 14 ? "," : "");
                    if (pos % 16 == 14) {
                        printLine(bw, sbLine.toString(), includeComments ? (pos == 14 ? "Color " + spriteColors[i] : "") : null);
                        sbLine.delete(0, sbLine.length());
                    }
                }
            }
        }
        if (colorMode == COLOR_MODE_ECM_2 || colorMode == COLOR_MODE_ECM_3) {
            if (includeComments) {
                printLine(bw, "****************************************", false);
                printLine(bw, "* Sprite Patterns Plane 1", false);
                printLine(bw, "****************************************", false);
            }
            sbLine.delete(0, sbLine.length());
            for (int i = startSprite; i <= endSprite; i++) {
                if (spriteGrids.get(i) != null) {
                    String hexstr = Globals.getSpriteHexString(spriteGrids.get(i), 2).toUpperCase();
                    sbLine.append("S1_").append(i).append(":\n  DATA BYTE ");
                    for (int pos = 0; pos < 64; pos += 2) {
                        if (pos > 0 && pos % 16 == 0) {
                            sbLine.append("       DATA BYTE ");
                        }
                        sbLine.append("$").append(hexstr, pos, pos + 2).append(pos % 14 != 12 ? "," : "");
                        if (pos % 14 == 12) {
                            printLine(bw, sbLine.toString(), includeComments);
                            sbLine.delete(0, sbLine.length());
                        }
                    }
                }
            }
        }
        if (colorMode == COLOR_MODE_ECM_3) {
            if (includeComments) {
                printLine(bw, "****************************************", false);
                printLine(bw, "* Sprite Patterns Plane 2", false);
                printLine(bw, "****************************************", false);
            }
            sbLine.delete(0, sbLine.length());
            for (int i = startSprite; i <= endSprite; i++) {
                if (spriteGrids.get(i) != null) {
                    String hexstr = Globals.getSpriteHexString(spriteGrids.get(i), 4).toUpperCase();
                    sbLine.append("S2_").append(i).append(":\n  DATA BYTE ");
                    for (int pos = 0; pos < 64; pos += 2) {
                        if (pos > 0 && pos % 16 == 0) {
                            sbLine.append("  DATA BYTE ");
                        }
                        sbLine.append("$").append(hexstr, pos, pos + 2).append(pos % 16 != 12 ? "," : "");
                        if (pos % 16 == 12) {
                            printLine(bw, sbLine.toString(), includeComments);
                            sbLine.delete(0, sbLine.length());
                        }
                    }
                }
            }
        }
    }

    private void writeMapData(int compression, boolean includeComments, boolean currMapOnly, boolean includeSpriteData, Map<ECMPalette, Integer> paletteMap, StringBuilder sbLine, BufferedWriter bw) throws Exception {
        if (includeComments) {
            printLine(bw, "****************************************", false);
            printLine(bw, "* Map Data", false);
            printLine(bw, "****************************************", false);
        }
        printLine(bw, "MCOUNT:\n  DATA BYTE " + (currMapOnly ? 1 : mapEditor.getMapCount()), includeComments);
        for (int m = 0; m < mapEditor.getMapCount(); m++) {
            if (!currMapOnly || m == mapEditor.getCurrentMapId()) {
                int[][] mapToSave = mapEditor.getMapData(m);
                if (includeComments) {
                    printLine(bw, "* == Map #" + m + " == ", false);
                }
                printLine(bw, "MC" + m + ":\n  DATA BYTE " + mapEditor.getScreenColor(m), includeComments);
                sbLine.delete(0, sbLine.length());
                sbLine.append("MS").append(m).append(":\n  DATA $");
                sbLine.append(Globals.toHexString(mapToSave[0].length, 4));
                sbLine.append(", $").append(Globals.toHexString(mapToSave.length, 4));
                sbLine.append(", $").append(Globals.toHexString(mapToSave[0].length * mapToSave.length, 4));
                printLine(bw, sbLine.toString(), includeComments ? "Width, Height, Size" : null);
                sbLine.delete(0, sbLine.length());
                if (compression == MagellanExportDialog.COMPRESSION_NONE) {
                    boolean isByteContent = Globals.isByteGrid(mapToSave);
                    boolean isFirstByte;
                    isFirstByte = true;
                    for (int y = 0; y < mapToSave.length; y++) {
                        if (includeComments) {
                            printLine(bw, "* -- Map Row " + y + " -- ", false);
                        }
                        int rowLength = mapToSave[y].length;
                        boolean useBytes = true;//rowLength % 2 == 1 && isByteContent;
                        String directive = useBytes ? "  DATA BYTE" : "DATA";
                        for (int cl = 0; cl < Math.ceil((double) rowLength / 8); cl++) {
                            if (y == 0 && cl == 0) {
                                sbLine.append("MD").append(m).append(":\n").append(directive).append(" ");
                            }
                            else {
                                sbLine.append(directive).append("  ");
                            }
                            for (int colpos = (cl * 8); colpos < Math.min((cl + 1) * 8, rowLength); colpos++) {
                                if (isFirstByte) {
                                    if (colpos > (cl * 8)) {
                                        sbLine.append(", ");
                                    }
                                    sbLine.append("$");
                                }
                                int value = mapToSave[y][colpos];
                                if (value == MapCanvas.NOCHAR) {
                                    value = 0;
                                }
                                sbLine.append(Globals.toHexString(value & 0xff, 2));
                                if (!useBytes && isByteContent) {
                                    isFirstByte = !isFirstByte;
                                }
                            }
                            printLine(bw, sbLine.toString(), includeComments);
                            sbLine.delete(0, sbLine.length());
                        }
                    }
                }
                else if (compression == MagellanExportDialog.COMPRESSION_RLE_BYTE) {
                    // RLE compression (byte)
                    // We assume all characters are < 128. If msb is set, the next byte determines
                    // how many times (2 - 256) the current byte (with msb cleared) should be repeated.
                    // A repeat count of 0 is used as end marker.
                    System.out.println("Uncompressed size: " + (mapToSave.length * mapToSave[0].length));
                    int i = 0;
                    int n = 0;
                    int last = -1;
                    int count = 0;
                    for (int[] row : mapToSave) {
                        for (int current : row) {
                            if (last != -1) {
                                if (current == last && count < 255) {
                                    // Same byte, increment count
                                    count++;
                                } else if (count > 0) {
                                    // End of run of bytes
                                    i = printByte(bw, sbLine, i, last | 128);
                                    i = printByte(bw, sbLine, i, count);
                                    count = 0;
                                    n += 2;
                                } else {
                                    // Different byte
                                    i = printByte(bw, sbLine, i, last);
                                    n++;
                                }
                            }
                            last = current;
                        }
                    }
                    if (count > 0) {
                        i = printByte(bw, sbLine, i, last | 128);
                        i = printByte(bw, sbLine, i, count);
                        n += 2;
                    }
                    else {
                        // Different byte
                        i = printByte(bw, sbLine, i, last);
                        n++;
                    }
                    // End marker
                    i = printByte(bw, sbLine, i, 128);
                    i = printByte(bw, sbLine, i, 0);
                    n += 2;
                    printLine(bw, sbLine.toString(), false);
                    n += i;
                    System.out.println("Compressed size: " + n);
                }
                else if (compression == MagellanExportDialog.COMPRESSION_RLE_WORD) {
                    // RLE compression (word)
                    // We assume all characters are < 128. If msb of the MSB is set, the byte following the
                    // current word determines how many times (2 - 256) the current word (with msb cleared)
                    // should be repeated. A repeat count of 0 is used as end marker.
                    System.out.println("Uncompressed size: " + (mapToSave.length * mapToSave[0].length));
                    int i = 0;
                    int n = 0;
                    int current;
                    int last = -1;
                    int count = 0;
                    for (int[] row : mapToSave) {
                        for (int x = 0; x < mapToSave[0].length - 1; x += 2) {
                            current = row[x] << 8 | row[x + 1];
                            if (last != -1) {
                                if (current == last && count < 255) {
                                    // Same word, increment count
                                    count++;
                                } else if (count > 0) {
                                    // End of run of words
                                    i = printByte(bw, sbLine, i, (last & 0xFF00) >> 8 | 128);
                                    i = printByte(bw, sbLine, i, last & 0x00FF);
                                    i = printByte(bw, sbLine, i, count);
                                    count = 0;
                                    n += 3;
                                } else {
                                    // Different byte
                                    i = printByte(bw, sbLine, i, (last & 0xFF00) >> 8);
                                    i = printByte(bw, sbLine, i, last & 0x00FF);
                                    n += 2;
                                }
                            }
                            last = current;
                        }
                    }
                    if (count > 0) {
                        i = printByte(bw, sbLine, i, (last & 0xFF00) >> 8 | 128);
                        i = printByte(bw, sbLine, i, last & 0x00FF);
                        i = printByte(bw, sbLine, i, count);
                        n += 2;
                    }
                    else {
                        // Different byte
                        i = printByte(bw, sbLine, i, (last & 0xFF00) >> 8);
                        i = printByte(bw, sbLine, i, last & 0x00FF);
                        n++;
                    }
                    // End marker
                    i = printByte(bw, sbLine, i, 128);
                    i = printByte(bw, sbLine, i, 0);
                    i = printByte(bw, sbLine, i, 0);
                    n += 3;
                    printLine(bw, sbLine.toString(), false);
                    n += i;
                    System.out.println("Compressed size: " + n);
                }
                else if (compression == MagellanExportDialog.COMPRESSION_META_2 || compression == MagellanExportDialog.COMPRESSION_META_4 || compression == MagellanExportDialog.COMPRESSION_META_8) {
                    int size = compression == MagellanExportDialog.COMPRESSION_META_2 ? 2 : compression == MagellanExportDialog.COMPRESSION_META_4 ? 4 : 8;
                    if (mapToSave.length % size != 0) {
                        throw new Exception("Map height is not a multiple of " + compression);
                    }
                    if (mapToSave[0].length % size != 0) {
                        throw new Exception("Map width is not a multiple of " + compression);
                    }
                    int height = mapToSave.length / size;
                    int width = mapToSave[0].length / size;
                    Map<String, MetaTile> metaTileLookup = new HashMap<>();
                    MetaTile[][] metaTileMap = new MetaTile[height][width];
                    int n = 0;
                    for (int y = 0; y < height; y++) {
                        for (int x = 0; x < width; x++) {
                            StringBuilder keyBuffer = new StringBuilder();
                            int[] tiles = new int[size * size];
                            for (int y1 = 0; y1 < size; y1++) {
                                for (int x1 = 0; x1 < size; x1++) {
                                    int tile = mapToSave[y * size + y1][x * size + x1];
                                    keyBuffer.append(Globals.toHexString(tile, 2));
                                    tiles[y1 * size + x1] = tile;
                                }
                            }
                            String key = keyBuffer.toString();
                            MetaTile metaTile = metaTileLookup.get(key);
                            if (metaTile == null) {
                                metaTile = new MetaTile(n++, tiles);
                                metaTileLookup.put(key, metaTile);
                            }
                            metaTileMap[y][x] = metaTile;
                        }
                    }
                    System.out.println("Number of meta tiles: " + metaTileLookup.size());
                    if (metaTileLookup.size() > 256) {
                        throw new Exception("Cannot support more than 256 meta tiles (" + metaTileLookup.size() + " found)");
                    }
                    boolean first = true;
                    n = 0;
                    for (MetaTile[] row : metaTileMap) {
                        for (MetaTile metaTile : row) {
                            n = printByte(bw, sbLine, n, metaTile.getNumber(), first ? "MD" + m : null);
                            first = false;
                        }
                    }
                    printLine(bw, sbLine.toString(), false);
                    if (includeComments) {
                        printLine(bw, "****************************************", false);
                        printLine(bw, "* Meta Tiles", false);
                        printLine(bw, "****************************************", false);
                    }
                    ArrayList<MetaTile> metaTiles = new ArrayList<>(metaTileLookup.values());
                    metaTiles.sort(Comparator.comparingInt(MetaTile::getNumber));
                    for (MetaTile metaTile : metaTiles) {
                        sbLine.delete(0, sbLine.length());
                        n = 0;
                        first = true;
                        int[] tiles = metaTile.getTiles();
                        for (int tile : tiles) {
                            n = printByte(bw, sbLine, n, tile, first ? "MT" + m + Globals.padl(metaTile.getNumber(), 3) : null);
                            first = false;
                        }
                        if (metaTile.getTiles().length < 8) {
                            printLine(bw, sbLine.toString(), false);
                        }
                    }
                }
                else if (compression == MagellanExportDialog.COMPRESSION_NYBBLES) {
                    String hexChunk;
                    for (int y = 0; y < mapToSave.length; y++) {
                        if (includeComments) {
                            printLine(bw, "* -- Map Row " + y + " -- ", false);
                        }
                        for (int cl = 0; cl < Math.ceil((double) mapToSave[y].length / 16); cl++) {
                            if (y == 0 && cl == 0) {
                                sbLine.append("MD").append(m).append(":\n  DATA BYTE ");
                            }
                            else {
                                sbLine.append("       DATA BYTE ");
                            }
                            for (int colpos = (cl * 16); colpos < Math.min((cl + 1) * 16, mapToSave[y].length); colpos++) {
                                if (colpos % 4 == 0) {
                                    if (colpos > (cl * 16)) {
                                        sbLine.append(",");
                                    }
                                    sbLine.append("$");
                                }
                                int value = mapToSave[y][colpos];
                                if (value >= 16) {
                                    throw new RuntimeException("Compression mode not supported for characters >= 16");
                                }
                                hexChunk = value != MapCanvas.NOCHAR ? Integer.toHexString(value).toUpperCase() : "0";
                                sbLine.append(hexChunk);
                            }
                            printLine(bw, sbLine.toString(), includeComments);
                            sbLine.delete(0, sbLine.length());
                        }
                    }
                }
                else {
                    throw new RuntimeException("Compression mode not yet supported.");
                }
                if (includeSpriteData) {
                    writeSpriteLocations(includeComments, paletteMap, bw, m);
                }
            }
        }
    }

    private void writeSpriteLocations(boolean includeComments, Map<ECMPalette, Integer> paletteMap, BufferedWriter bw, int m) throws IOException {
        HashMap<Point, ArrayList<Integer>> spriteMap = mapEditor.getSpriteMap(m);
        if (spriteMap.size() > 0) {
            if (includeComments) {
                printLine(bw, "****************************************", false);
                printLine(bw, "* Sprite Locations", false);
                printLine(bw, "****************************************", false);
            }
            boolean smallMap = mapEditor.getGridWidth() <= 32 && mapEditor.getGridHeight() <= 32;
            boolean first = true;
            List<Point> sortedPoints = new ArrayList<>(spriteMap.keySet());
            sortedPoints.sort(Comparator.comparingInt(p -> ((Point) p).y).thenComparingInt(p -> ((Point) p).x));
            for (Point p : sortedPoints) {
                ArrayList<Integer> spriteList = spriteMap.get(p);
                for (Integer spriteNum : spriteList) {
                    int color = (colorMode == COLOR_MODE_GRAPHICS_1 || colorMode == COLOR_MODE_BITMAP) ? spriteColors[spriteNum] : paletteMap.get(ecmSpritePalettes[spriteNum]) * (colorMode == COLOR_MODE_ECM_3 ? 2 : 1);
                    printLine(bw, (first ? "SL" + m + ":\n": "") +
                            (smallMap ? "  DATA BYTE " + ((p.y - 1) & 0xFF) : "  DATA BYTE " + p.y) + "," + p.x + "," + spriteNum * 4 + "," + color, includeComments ? (first ? "y, x, pattern#, color#" : "") : null);
                    first = false;
                }
            }
        }
    }
}
