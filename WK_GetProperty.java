import ij.*;
import ij.gui.GenericDialog;
import ij.process.*;
import ij.plugin.filter.*;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;

/*
 * The MIT License
 *
 * Copyright 2016 Takehito Nishida.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

/**
 * System.getProperty.
 */
public class WK_GetProperty implements ExtendedPlugInFilter
{
    // constant var.
    private final int FLAGS = NO_IMAGE_REQUIRED;
    private static final String[] LIST_KEYS = {
        "awt.toolkit",
        "file.encoding",
        "file.encoding.pkg",
        "file.separator",
        "java.awt.graphicsenv",
        "java.awt.printerjob",
        "java.class.path",
        "java.class.version",
        "java.endorsed.dirs",
        "java.ext.dirs",
        "java.home",
        "java.io.tmpdir",
        "java.library.path",
        "java.runtime.name",
        "java.runtime.version",
        "java.specification.name",
        "java.specification.vendor",
        "java.specification.version",
        "java.vendor",
        "java.vendor.url",
        "java.vendor.url.bug",
        "java.version",
        "java.vm.info",
        "java.vm.name",
        "java.vm.specification.name",
        "java.vm.specification.vendor",
        "java.vm.specification.version",
        "java.vm.vendor",
        "java.vm.version",
        "line.separator",
        "os.arch",
        "os.name",
        "os.version",
        "path.separator",
        "sun.arch.data.model",
        "sun.boot.class.path",
        "sun.boot.library.path",
        "sun.cpu.endian",
        "sun.cpu.isalist",
        "sun.desktop",
        "sun.io.unicode.encoding",
        "sun.java.launcher",
        "sun.jnu.encoding",
        "sun.management.compiler",
        "sun.os.patch.level",
        "user.country",
        "user.dir",
        "user.home",
        "user.language",
        "user.name",
        "user.timezone",
        "user.variant"};

    // static var.
    private static int ind_list = 7;

    @Override
    public int showDialog(ImagePlus ip, String command, PlugInFilterRunner pifr)
    {
        GenericDialog gd = new GenericDialog(command.trim() + "...");
        gd.addChoice("key", LIST_KEYS, LIST_KEYS[ind_list]);
        gd.showDialog();

        if (gd.wasCanceled())
        {
            return DONE;
        }
        else
        {
            ind_list = (int)gd.getNextChoiceIndex();
            return FLAGS;
        }
    }
    
    @Override
    public void setNPasses(int i)
    {
        // do nothing
    }

    @Override
    public int setup(String string, ImagePlus ip)
    {
        // do nothing
        return FLAGS;
    }

    @Override
    public void run(ImageProcessor ip)
    {
        String key = LIST_KEYS[ind_list];
        String val = System.getProperty(key);
        val = val.replaceAll(";", "\r\n");
        
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        StringSelection selection = new StringSelection(val);
        clipboard.setContents(selection, null);
        
        IJ.showMessageWithCancel(key, val);
    }
}
