package graph_sum;
import java.util.Comparator;
import org.javatuples.Pair;

public class EdgeCompare implements Comparator<Pair<Integer, Integer>>
{   
    int[] supernode_sizes;

    public EdgeCompare(int[] supernode_sizes) {
        this.supernode_sizes = supernode_sizes;
    }

    public int compare(Pair<Integer, Integer> P1, Pair<Integer, Integer> P2) {
        int P1_val = supernode_sizes[P1.getValue0()] * supernode_sizes[P1.getValue1()];
        int P2_val = supernode_sizes[P2.getValue0()] * supernode_sizes[P2.getValue1()];

        if (P1_val < P2_val) { return -1; }
        else if (P1_val > P2_val) { return 1; }
        else { return 0; }
    }
}