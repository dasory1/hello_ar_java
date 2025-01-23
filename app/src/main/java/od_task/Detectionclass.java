package od_task;
import java.util.List;
import android.graphics.RectF;

//import org.tensorflow.lite.support.label.Category;

public class Detectionclass {
    private RectF boundingBox;
    private Categoryclass categories;

    public Detectionclass(RectF boundingBox, Categoryclass categories) {
        this.boundingBox = boundingBox;
        this.categories = categories;
    }

    public RectF getBoundingBox() {
        return boundingBox;
    }

    public Categoryclass getCategories() {
        return categories;
    }
}
