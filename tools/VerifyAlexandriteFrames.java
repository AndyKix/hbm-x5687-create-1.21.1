import java.awt.image.BufferedImage;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Independent decoder verification of the lossless asset representation change. JDK 21 source launcher. */
public final class VerifyAlexandriteFrames {
    public static void main(String[] args) throws Exception {
        Path upstream = Path.of(args[0]);
        BufferedImage source = ImageIO.read(upstream.resolve("src/main/resources/assets/hbm/textures/items/gem_alexandrite.png").toFile());
        if (source.getWidth() != 16 || source.getHeight() != 256) throw new AssertionError("Unexpected original dimensions");
        for (int frame = 0; frame < 16; frame++) {
            BufferedImage actual = ImageIO.read(Path.of("src/main/resources/assets/hbm/textures/item/gem_alexandrite_" + frame + ".png").toFile());
            if (actual.getWidth() != 16 || actual.getHeight() != 16) throw new AssertionError("Wrong frame dimensions");
            for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++)
                if (source.getRGB(x, frame * 16 + y) != actual.getRGB(x, y)) throw new AssertionError("Changed pixel at frame " + frame + ", " + x + ", " + y);
        }
        System.out.println("ImageIO PASS: all 4096 original RGBA pixels preserved across 16 light frames.");
    }
}
