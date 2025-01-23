package od_task;
public class DetectionOptions {
    private int maxResults;
    private float scoreThreshold;

    public DetectionOptions(int maxResults, float scoreThreshold) {
        this.maxResults = maxResults;
        this.scoreThreshold = scoreThreshold;
    }

    public int getMaxResults() {
        return maxResults;
    }

    public float getScoreThreshold() {
        return scoreThreshold;
    }
}
