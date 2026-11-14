// Draws the icon of the application: the logo on a rounded square of one colour, as KaTTY's (`app/make-icon.java` of KaTTY, whose square, colour
// and shadow these are). It is the shape that macOS wants the icon of an application to have: since macOS 26, it shrinks an icon of another shape,
// such as the bare logo, and puts it on a grey rounded square. The square is the one of the grid of the icons of macOS: 824 pixels, with corners
// of a radius of 185.4, in the middle of a canvas of 1024 that is transparent around it, but for the shadow that the square casts, as the icons of
// the other applications do.
// Run by the build with the java of its toolchain, in a process of its own: `java -Djava.awt.headless=true make-icon.java <logo.png> <#rrggbb> <icon.png>`.
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

class MakeIcon {
    static final int CANVAS = 1024;
    static final double SQUARE = 824;
    static final double RADIUS = 185.4;
    // KaSH's logo is wider than high, and its file has transparent margins that are not even: what is drawn of it (its pixels that are not
    // transparent) is fitted in this part of the square, its proportions kept, and centered.
    static final double LOGO = 0.9;
    // The shadow that macOS draws under the icons of the other applications in the Dock, at 46 points: black at 28 %, 21 pixels down (of the 1024),
    // blurred over 40 (the standard deviation of a Gaussian blur), measured on the icon of Visual Studio Code. macOS draws it about 2 points wide at
    // any size, so it is relatively smaller in a bigger icon: the Dock is where this image is shown as it is (see `appIcon` in `build.gradle.kts`).
    static final float SHADOW_OPACITY = 0.28f;
    static final double SHADOW_OFFSET = 21;
    static final double SHADOW_BLUR = 40;

    public static void main(String[] args) throws IOException {
        BufferedImage logo = ImageIO.read(new File(args[0]));
        Color background = Color.decode(args[1]);
        double inset = (CANVAS - SQUARE) / 2;
        Shape square = new RoundRectangle2D.Double(inset, inset, SQUARE, SQUARE, RADIUS * 2, RADIUS * 2);

        BufferedImage icon = new BufferedImage(CANVAS, CANVAS, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = smooth(icon.createGraphics());
        // The shadow is blurred on a bigger canvas, since the blur leaves a band as wide as its reach along the edges blank.
        graphics.drawImage(shadow(square), -reach(), -reach(), null);
        graphics.setColor(background);
        graphics.fill(square);
        Rectangle drawn = drawnBounds(logo);
        double scale = Math.min(SQUARE * LOGO / drawn.width, SQUARE * LOGO / drawn.height);
        int width = (int) Math.round(drawn.width * scale);
        int height = (int) Math.round(drawn.height * scale);
        int x = (CANVAS - width) / 2;
        int y = (CANVAS - height) / 2;
        graphics.drawImage(logo, x, y, x + width, y + height, drawn.x, drawn.y, drawn.x + drawn.width, drawn.y + drawn.height, null);
        graphics.dispose();
        ImageIO.write(icon, "png", new File(args[2]));
    }

    // The smallest rectangle of the image that holds every pixel that is not transparent.
    static Rectangle drawnBounds(BufferedImage image) {
        int left = image.getWidth(), top = image.getHeight(), right = -1, bottom = -1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) != 0) {
                    left = Math.min(left, x);
                    top = Math.min(top, y);
                    right = Math.max(right, x);
                    bottom = Math.max(bottom, y);
                }
            }
        }
        if (right < 0) return new Rectangle(0, 0, image.getWidth(), image.getHeight());
        return new Rectangle(left, top, right - left + 1, bottom - top + 1);
    }

    static Graphics2D smooth(Graphics2D graphics) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        return graphics;
    }

    // How far the blur reaches: three standard deviations, beyond which a Gaussian is nothing.
    static int reach() {
        return (int) Math.ceil(SHADOW_BLUR * 3);
    }

    // The square, moved down, in translucent black, blurred: a Gaussian blur along each axis in turn, which is the same as both at once. The image
    // is the canvas with a margin of the blur's reach all around.
    static BufferedImage shadow(Shape square) {
        int reach = reach();
        BufferedImage shadow = new BufferedImage(CANVAS + 2 * reach, CANVAS + 2 * reach, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D graphics = smooth(shadow.createGraphics());
        graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, SHADOW_OPACITY));
        graphics.setColor(Color.BLACK);
        graphics.fill(AffineTransform.getTranslateInstance(reach, reach + SHADOW_OFFSET).createTransformedShape(square));
        graphics.dispose();
        float[] weights = gaussian(SHADOW_BLUR);
        BufferedImage across = new ConvolveOp(new Kernel(weights.length, 1, weights), ConvolveOp.EDGE_ZERO_FILL, null).filter(shadow, null);
        return new ConvolveOp(new Kernel(1, weights.length, weights), ConvolveOp.EDGE_ZERO_FILL, null).filter(across, null);
    }

    // The weights of a Gaussian of this standard deviation, out to its reach, which add up to 1.
    static float[] gaussian(double sigma) {
        int radius = reach();
        float[] weights = new float[radius * 2 + 1];
        float sum = 0;
        for (int i = -radius; i <= radius; i++) {
            weights[i + radius] = (float) Math.exp(-(i * i) / (2 * sigma * sigma));
            sum += weights[i + radius];
        }
        for (int i = 0; i < weights.length; i++) weights[i] /= sum;
        return weights;
    }
}
