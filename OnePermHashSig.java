package graph_sum;

public class OnePermHashSig {
    int sigSize;
    int[] sig;

    public OnePermHashSig(int sigSize) {
        this.sigSize = sigSize;
        sig = new int[sigSize];

        for (int i = 0; i < sigSize; i++) {
            sig[i] = -1;
        }
    }

    public OnePermHashSig(int[] sig) {
        sigSize = sig.length;
        this.sig = new int[sigSize];
        this.sig = sig;
    }

    public boolean equals(OnePermHashSig otherSig) {
        for (int i = 0; i < sigSize; i++) {
            if (sig[i] != otherSig.sig[i]) { return false; }
        }

        return true;
    }

    public boolean unassigned() {
        for (int i = 0; i < sigSize; i++) {
            if (sig[i] != -1) {
                return false;
            }
        }
        
        return true;
    }

    public static int compare(OnePermHashSig a, OnePermHashSig b) {
        for (int i = 0; i < a.sigSize; i++) {
            if (a.sig[i] != b.sig[i]) {
                return a.sig[i] - b.sig[i];
            }
        }

        return 0;
    }
}