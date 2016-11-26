import ij.IJ;
import ij.ImagePlus;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import java.awt.AWTEvent;

/**
 * Min filter for a binary image.
 * 
 * * A image must be formed of 0 or 255.
 */
public class WK_ErodeWhite implements ExtendedPlugInFilter, DialogListener
{
    // constant var.
    public static final int ERR_OK                =  0;    //OK
    public static final int ERR_NG                = -1;    //NG
    public static final int ERR_BAD_ARGUMENT      = -2;    //bad argument
    public static final int ERR_MULTIPLES_OF_FOUR = -3;    //image width is not multiples of four
    public static final int ERR_TAP_SIZE          = -4;    //tap size is not odd

    public static final int UBYTE_MAX = 255;

    private final int FLAGS = DOES_8G | CONVERT_TO_FLOAT | DOES_STACKS | PARALLELIZE_STACKS;

    // staic var.
    private static int tap_x;
    private static int tap_y;

    @Override
    public void setNPasses(int arg0)
    {
        // do nothing
    }

    @Override
    public int showDialog(ImagePlus imp, String cmd, PlugInFilterRunner pfr)
    {
        tap_x = tap_x == 0 ? 3 : tap_x;
        tap_y = tap_y == 0 ? 3 : tap_y;

        GenericDialog gd = new GenericDialog(cmd.trim() + "...");

        gd.addMessage("The values except 255 is dealt with with 0.");
        gd.addNumericField("tap_x", tap_x, 0);
        gd.addNumericField("tap_y", tap_y, 0);
        gd.addPreviewCheckbox(pfr);
        gd.addDialogListener(this);

        gd.showDialog();

        if (gd.wasCanceled())
        {
            return DONE;
        }
        else
        {
            return IJ.setupDialog(imp, FLAGS);
        }
    }

    @Override
    public boolean dialogItemChanged(GenericDialog gd, AWTEvent awte)
    {
        tap_x = (int)gd.getNextNumber();
        tap_y = (int)gd.getNextNumber();

        if(tap_x <= 0) { IJ.showStatus("ERR : tap_x <= 0"); return false; }
        if(tap_y <= 0) { IJ.showStatus("ERR : tap_y <= 0"); return false; }
        if((tap_x % 2) ==0) { IJ.showStatus("ERR : tap_x is not odd."); return false; }
        if((tap_y % 2) ==0) { IJ.showStatus("ERR : tap_y is not odd."); return false; }

        IJ.showStatus("WK_ErodeWhite");
        return true;
    }    
    
    @Override
    public void run(ImageProcessor ip)
    {
        int ret = ERR_OK;

        float[] dst = (float[])((FloatProcessor)ip).getPixels();
        int imw = ip.getWidth();
        int imh = ip.getHeight();
        int numpix = imw * imh;

        float[] src = new float[numpix];
        System.arraycopy(dst, 0, src, 0, numpix);
       
        ret = fltrMin_Binary(src, dst, imw, imh, tap_x, tap_y);

        if(ret != ERR_OK)
        {
            IJ.showMessage(errCode2Message(ret));
        }
    }

    @Override
    public int setup(String arg0, ImagePlus imp)
    {
        if (imp == null)
        {
            IJ.noImage();
            return DONE;
        }
        else
        {
            return FLAGS;
        }
    }

    private int fltrMin_Binary(float[] pSrc, float[] pDst, int imw, int imh, int tapX, int tapY)
    {
        int ret = ERR_OK;
        int numpix = imw * imh;

        if(ret == ERR_OK && (imw % 4) != 0)
        {
            ret = ERR_MULTIPLES_OF_FOUR;
        }

        if(ret == ERR_OK && (imw <= 0 || imh <= 0 || tapX <= 0 || tapY <= 0))
        {
            ret = ERR_BAD_ARGUMENT;
        }

        if(ret == ERR_OK && ((tapX % 2) ==0 || (tapY % 2) ==0))
        {
            ret = ERR_TAP_SIZE;
        }

        if(ret == ERR_OK && (tapX == 1 && tapY == 1))
        {
            System.arraycopy(pSrc, 0, pDst, 0, pSrc.length);
            return ret;
        }

        if( ret == ERR_OK )
        {
            int mrgn_tp_bttm = (tapY - 1) / 2;
            int mrgn_lft_rght = (tapX - 1) / 2;

            int[] line_buffer = new int[imw];

            float[] ini_buf = new float[numpix];
            System.arraycopy(ini_buf, 0, pDst, 0, numpix);

            int thresh = UBYTE_MAX * tapX * tapY;

            for(int y = mrgn_tp_bttm; y < imh - mrgn_tp_bttm; y++)
            {
                if(y == mrgn_tp_bttm)
                {
                    for(int dy = 0; dy < tapY; dy++)
                    {
                        for(int dx = 0; dx < imw; dx++)
                        {
                            line_buffer[dx] += (int)pSrc[dx + imw * dy];
                        }
                    }
                }
                else
                {
                    for(int x = 0; x < imw; x++)
                    {
                        line_buffer[x] = line_buffer[x] - (int)pSrc[x + imw * (y - mrgn_tp_bttm - 1)] + (int)pSrc[x + imw * (y + mrgn_tp_bttm)];
                    }
                }

                int sum_value = 0;

                for(int i = 0; i < tapX; i++)
                {
                    sum_value += line_buffer[i];
                }

                if(sum_value == thresh)
                {
                    pDst[mrgn_lft_rght + imw * y] = (float)UBYTE_MAX;
                }

                for(int x = mrgn_lft_rght + 1; x < imw - mrgn_lft_rght; x++)
                {
                    sum_value = sum_value - line_buffer[x - (mrgn_lft_rght + 1)] + line_buffer[x + mrgn_lft_rght];

                    if(sum_value == thresh)
                    {
                        pDst[x + imw * y] = (float)UBYTE_MAX;
                    }
                }
            }
        }

        return ret;
    }

    private String errCode2Message(int err)
    {
        String output = "";

        if (ERR_OK <= err) { output = "OK"; }
        else if (err == ERR_NG) { output = "ERR"; }
        else if (err == ERR_BAD_ARGUMENT) { output = "ERR_BAD_ARGUMENT"; }
        else if (err == ERR_MULTIPLES_OF_FOUR) { output = "ERR_MULTIPLES_OF_FOUR"; }
        else if (err == ERR_TAP_SIZE) { output = "ERR_TAP_SIZE"; }
        else { output = "UNKNOWN_ERR (" + String.valueOf(err) + ")"; }

        return output;
    }
}
