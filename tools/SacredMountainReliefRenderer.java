import com.liy.ancientdragon.worldgen.SacredMountainShape;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Small dependency-free design diagnostic for the authored Sacred Mountain height field. */
public final class SacredMountainReliefRenderer {
    private SacredMountainReliefRenderer() {
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 1) {
            throw new IllegalArgumentException("Usage: SacredMountainReliefRenderer <output.png>");
        }
        SacredMountainShape shape = new SacredMountainShape(SacredMountainShape.DEFAULT_SEED);
        int size = SacredMountainShape.DIAMETER;
        int[][] height = new int[size][size];
        for (int imageZ = 0; imageZ < size; imageZ++) {
            int localZ = imageZ + SacredMountainShape.MIN_COORDINATE;
            for (int imageX = 0; imageX < size; imageX++) {
                int localX = imageX + SacredMountainShape.MIN_COORDINATE;
                height[imageZ][imageX] = shape.heightAt(localX, localZ);
            }
        }

        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        double lightX = -0.58D;
        double lightY = 0.62D;
        double lightZ = -0.53D;
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                int center = height[z][x];
                if (center <= 0) {
                    image.setRGB(x, z, new Color(15, 22, 25).getRGB());
                    continue;
                }
                int west = height[z][Math.max(0, x - 2)];
                int east = height[z][Math.min(size - 1, x + 2)];
                int north = height[Math.max(0, z - 2)][x];
                int south = height[Math.min(size - 1, z + 2)][x];
                double normalX = west - east;
                double normalY = 4.0D;
                double normalZ = north - south;
                double normalLength = Math.sqrt(
                        (normalX * normalX) + (normalY * normalY) + (normalZ * normalZ));
                double illumination = ((normalX * lightX) + (normalY * lightY) + (normalZ * lightZ))
                        / normalLength;
                double altitude = Math.min(1.0D, center / 282.0D);
                int shade = (int) Math.round(Math.clamp(
                        72.0D + (illumination * 104.0D) + (altitude * 46.0D), 22.0D, 232.0D));
                boolean contour = (center / 8) != (east / 8) || (center / 8) != (south / 8);
                if (contour) {
                    shade = Math.max(18, shade - 17);
                }
                image.setRGB(x, z, new Color(shade, shade + 2, shade + 3).getRGB());
            }
        }

        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setStroke(new BasicStroke(2.0F));
        graphics.setFont(new Font("Microsoft YaHei", Font.BOLD, 16));
        title(graphics, "神山 · 不灭灵魂火工程分布图");
        marker(graphics, -88, -168, "风暴峰", new Color(255, 82, 82));
        marker(graphics, -174, 72, "破碎冠峰", new Color(255, 82, 82));
        marker(graphics, 88, 176, "太阳长脊", new Color(255, 82, 82));
        soulFireCluster(graphics, -126, -142);
        soulFireCluster(graphics, -58, -186);
        soulFireCluster(graphics, -210, 38);
        soulFireCluster(graphics, -150, 116);
        soulFireCluster(graphics, -94, 148);
        soulFireCluster(graphics, 38, 132);
        soulFireCluster(graphics, 112, 158);
        soulFireCluster(graphics, 142, 194);
        legend(graphics);
        graphics.dispose();

        Path output = Path.of(arguments[0]).toAbsolutePath();
        Files.createDirectories(output.getParent());
        ImageIO.write(image, "png", output.toFile());
        System.out.println(output);
    }

    private static void marker(Graphics2D graphics, int localX, int localZ, String label, Color color) {
        int x = localX - SacredMountainShape.MIN_COORDINATE;
        int z = localZ - SacredMountainShape.MIN_COORDINATE;
        graphics.setColor(color);
        graphics.drawOval(x - 7, z - 7, 14, 14);
        graphics.drawString(label, x + 11, z - 7);
    }

    private static void soulFireCluster(Graphics2D graphics, int localX, int localZ) {
        int centerX = localX - SacredMountainShape.MIN_COORDINATE;
        int centerZ = localZ - SacredMountainShape.MIN_COORDINATE;
        Color glow = new Color(36, 226, 255, 50);
        graphics.setColor(glow);
        graphics.fillOval(centerX - 13, centerZ - 13, 26, 26);
        graphics.setColor(new Color(50, 235, 255, 160));
        graphics.fillOval(centerX - 7, centerZ - 7, 14, 14);
        graphics.setColor(new Color(190, 252, 255));
        graphics.fillOval(centerX - 3, centerZ - 3, 6, 6);
        graphics.setColor(new Color(77, 221, 255));
        graphics.fillOval(centerX - 13, centerZ + 5, 4, 4);
        graphics.fillOval(centerX + 8, centerZ - 11, 4, 4);
        graphics.fillOval(centerX + 10, centerZ + 8, 3, 3);
    }

    private static void title(Graphics2D graphics, String value) {
        graphics.setColor(new Color(4, 9, 10, 230));
        graphics.fillRoundRect(14, 14, 430, 44, 10, 10);
        graphics.setColor(Color.WHITE);
        graphics.drawString(value, 27, 43);
    }

    private static void legend(Graphics2D graphics) {
        int x = 16;
        int y = SacredMountainShape.DIAMETER - 62;
        graphics.setColor(new Color(4, 9, 10, 230));
        graphics.fillRoundRect(x, y, 330, 46, 10, 10);
        graphics.setColor(new Color(50, 235, 255));
        graphics.fillOval(x + 15, y + 13, 18, 18);
        graphics.setColor(Color.WHITE);
        graphics.drawString("不灭灵魂火（贴近山体空气）", x + 44, y + 30);
    }
}
