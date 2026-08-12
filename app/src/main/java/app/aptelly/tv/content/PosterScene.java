package app.aptelly.tv.content;

public final class PosterScene {
    public final String category;
    public final String eyebrow;
    public final String title;
    public final String summary;
    public final String imageUrl;
    public final String posterTitle;
    public final String posterUrl;
    public final int accentColor;
    public final int deepColor;

    public PosterScene(
            String category,
            String eyebrow,
            String title,
            String summary,
            String imageUrl,
            String posterTitle,
            String posterUrl,
            int accentColor,
            int deepColor
    ) {
        this.category = category;
        this.eyebrow = eyebrow;
        this.title = title;
        this.summary = summary;
        this.imageUrl = imageUrl;
        this.posterTitle = posterTitle;
        this.posterUrl = posterUrl;
        this.accentColor = accentColor;
        this.deepColor = deepColor;
    }
}
