package od_task;
public class Categoryclass {
    private String label;
    private float score;
    private float index;
    public Categoryclass(String label, float score, float index) {
        this.label = label;
        this.score = score;
        this.index = index;
    }

    public String getLabel() {
        return label;
    }
    public float getIndex() {
        return index;
    }
    public float getScore() {
        return score;
    }
}
