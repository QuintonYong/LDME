package graph_sum;

public class FourTuple implements Comparable<FourTuple> {
    int A, B, u, v;

    public FourTuple(int A, int B, int u, int v) {
        this.A = A;
        this.B = B;
        this.u = u;
        this.v = v;
    }

    public int compareTo(FourTuple other) {
        if (this.A < other.A || (this.A == other.A && this.B < other.B)) {
            return -1;
        } else if (this.A > other.A || (this.A == other.A && this.B > other.B)) {
            return 1;
        } else {
            return 0;
        }
    }
}
