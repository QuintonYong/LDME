package graph_sum;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Collections;
import java.util.concurrent.ThreadLocalRandom;
import java.util.Random;
import it.unimi.dsi.webgraph.ImmutableGraph;
import org.javatuples.Pair;
import java.io.FileWriter;
import java.io.File;
import java.util.Comparator;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.set.hash.TIntHashSet;
import gnu.trove.iterator.TIntIterator;
import java.util.Iterator;
import java.util.LinkedList;

public class Ldme {
	ImmutableGraph Gr;
	int n;
	
	int[] h;
	
	int[] S; //supernodes array; e.g. S[3]==2 means node 3 is in supernode 2.
	int[] I; //the first node array; 
			 //e.g. I[3]==5 means the first node in the supernode of node 3 is in index 5.
			 //e.g. I[4]==-1 means there is no supernode with id==4. 
	int[] J; //the next node array; 
			 //e.g. J[3]==9 means the next node in the supernode of node 3 is in index 9.	
	int[] F; //shingle array; 
			 //e.g. F[2]==8 means the shingle of supernode 2 is 8. 
             //e.g. F[2]==-1 means there is no supernode 2. 
    OnePermHashSig[] F_OPH;
	
	Integer[] G; //sorted group array; e.g. F[G[i]] <= F[G[i+1]] 
	int gstart = 0;  //the index in G for which we have a real group (not -1)

    int print_iteration_offset = 0; // run encode step after how many iterations

    int[] supernode_sizes;
    HashMap<Integer, TIntArrayList> sn_to_n;
    ArrayList<Pair<Integer, Integer>> P;
    TIntArrayList Cp_0, Cp_1;
    TIntArrayList Cm_0, Cm_1;

    double divideAndMergeTime;
    double encodeTime;
    double dropTime;

    double error_bound;
    double[] cv;

    int signatureLength;
	
	public Ldme (String basename, double error_bound) throws Exception {
		Gr = ImmutableGraph.loadMapped(basename);
		n = Gr.numNodes();
		h = new int[n];
		S = new int[n];
		I = new int[n];
        J = new int[n];	

		for(int i = 0; i < n; i++){
			h[i] = i;			
			S[i] = i;
			I[i] = i;
			J[i] = -1;
        }
        
        // global data structures
        supernode_sizes = new int[n];
        sn_to_n = new HashMap<Integer, TIntArrayList>();
        P = new ArrayList<Pair<Integer, Integer>>();
        Cp_0 = new TIntArrayList(); Cp_1 = new TIntArrayList();
        Cm_0 = new TIntArrayList(); Cm_1 = new TIntArrayList();

        cv = new double [n];
        divideAndMergeTime = 0;
        encodeTime = 0;
        dropTime = 0;
        this.error_bound = error_bound;
        signatureLength = 0;
	}

	void shuffleArray(){
	    Random rnd = new Random();
	    rnd = ThreadLocalRandom.current();
	    for (int i = h.length - 1; i > 0; i--) {
	      int index = rnd.nextInt(i + 1);
	      // Simple swap
	      int a = h[index];
	      h[index] = h[i];
	      h[i] = a;
	    }
    }
	
	void Divide() {
		//generate a random bijective hash function h : V to {1, . . ., |V| } 
		shuffleArray();
		F = new int[n];
		for (int A = 0; A < n; A++) 
			F[A] = -1;
		
		for (int A = 0; A < n; A++) {
			if (I[A] == -1) //A is not supernode; skip
				continue;
			
			F[A] = n; //one more than greatest possible value of n-1
			for (int v = I[A]; ; v = J[v]) {
				int fv = f(v);
				if (F[A] > fv)
					F[A] = fv;
				
				if (J[v] == -1)
					break;
			}
		}
		
		//sort groups
        G = new Integer[n];
        for (int i = 0; i < n; i++) G[i] = i;
        Arrays.sort(G, (o1,o2) -> Integer.compare(F[o1], F[o2]));
        
        //initialize gstart: the index in G for which we have a real group (not -1)
        gstart = 0;
        while (F[G[gstart]] == -1)
        	gstart++;
    }
    
    void DivideNew(int k_bins) {
        int bin_size = n / k_bins;

        if (n % k_bins != 0) { k_bins = k_bins + 1; }
        
        int[] rot_direction = new int[k_bins];
        Random random = new Random();
        for (int i = 0; i < k_bins; i++) {
            if (random.nextBoolean()) { rot_direction[i] = 1; }
            else { rot_direction[i] = -1; }
        }

        shuffleArray();
        F_OPH = new OnePermHashSig[n];
        for(int A=0; A<n ; A++) 
            F_OPH[A] = new OnePermHashSig(k_bins);

        for (int A = 0; A < n; A++) {
            if (I[A] == -1) continue;

            for (int v = I[A]; ; v=J[v]) {
                int[] neighbours = Gr.successorArray(v);

                for (int j = 0; j < neighbours.length; j++) {
                    int permuted_h = h[neighbours[j]];                   
                    int permuted_bin = permuted_h / bin_size;
                    
                    if (F_OPH[A].sig[permuted_bin] == -1 || permuted_h % bin_size < F_OPH[A].sig[permuted_bin]) {
                        F_OPH[A].sig[permuted_bin] = permuted_h % bin_size;
                    }
                }

                if(J[v]==-1)
					break;
            }

            // rotation
            for (int A_bin = 0; A_bin < k_bins; A_bin++) {
                int direction = rot_direction[A_bin];

                if (F_OPH[A].sig[A_bin] == -1) {
                    int i = (A_bin + direction) % k_bins;
                    if (i < 0) { i += k_bins; }

                    int counter = 0;
                    while (F_OPH[A].sig[i] == -1 && counter < k_bins) { 
                        i = (i + direction) % k_bins;
                        if (i < 0) { i += k_bins; }

                        counter++;
                    }
                    F_OPH[A].sig[A_bin] = F_OPH[A].sig[i];
                }               
            }
        }

        //sort groups
        G = new Integer[n];
        for(int i = 0; i < n; i++) G[i] = i;
        Arrays.sort(G, (o1,o2) -> OnePermHashSig.compare(F_OPH[o1], F_OPH[o2]));
        
         //initialize gstart: the index in G for which we have a real group (not -1)
         gstart = 0;
         while(F_OPH[G[gstart]].unassigned())
             gstart++;

    }	
	
	//shingle of a node
	int f(int v) {
		int fv = h[v]; // since we consider the shingle value of node and its neighbors, 
		// we initialize fv as the shingle value of node v. 
		int v_deg = Gr.outdegree(v);
		int[] v_succ = Gr.successorArray(v);
		for (int j = 0; j < v_deg; j++) {
			int u = v_succ[j];
			if (fv > h[u])
				fv = h[u];
		}
		return fv;
	}


	void merge(int number_groups, int tt, OnePermHashSig[] group_prop_0, int[] group_prop_1){
		int merges_number = 0; int idx = 0; //Responsible for number of times merging between a pair of supernodes happen.
        OnePermHashSig[] temp = new OnePermHashSig[n];
		double Threshold = 1 / ((tt + 1) * 1.0);
        int group_size = 0; 
        double[] jac_sim;
        for (int i = 0; i < n; i++) temp[i] = F_OPH[G[i]];

		int total_merge_successes = 0;
		int total_merge_fails = 0;
		// for each group of supernodes in the dividing step
		for (int i = 0 ; i < number_groups ; i++){

            int st_position = group_prop_1[i];
            group_size = groups_length(temp, group_prop_0[i], st_position) -1;
			if (group_size<2) continue;
			
			// Q is the current group of supernodes that we're merging (from dividing step)
			int[] Q = new int[group_size];
			int counter = 0;
			// create the group Q
			for (int j= st_position; j < (st_position + group_size); j++){
				Q[counter++] = G[j];
			}

			int merging_success = 0;
			int merging_fails = 0;
			HashMap<Integer, HashMap<Integer,Integer>> hm = create_W(Q, group_size);

			int initial_size = hm.size();
			while (hm.size()>1){
                Random rand = new Random();
                int A = rand.nextInt(initial_size);
                if (hm.get(A) == null)
                    continue;
                    
                double max = 0;
                idx = -1;
            
                for (int j = 0 ; j < initial_size; j++){
                    if (hm.get(j) == null)
                        continue;
                    if (j == A) continue;
                    jac_sim = JacSim_SavCost(hm.get(A), hm.get(j));
                    if (jac_sim[0] > max) {
                        max = jac_sim[0];
                        idx = j;
                    }
                } 
                
                    
                if (idx == -1) {
                    hm.remove(A);
                    continue;
                }
                
                double savings = costSaving(hm.get(A), hm.get(idx), Q[A], Q[idx]);
                if (savings >= Threshold){
                    HashMap<Integer,Integer> w_update = update_W(hm.get(A), hm.get(idx));
                    hm.replace(A, w_update);
                    hm.remove(idx);
                    Update_S(Q[A], Q[idx]);
                    merging_success++;
                    merges_number++;
                } else {
                    merging_fails++;
                    hm.remove(A);
                }
			}

			total_merge_successes += merging_success;
			total_merge_fails += merging_fails;
		}

	}

    void encode_lsh() {
        System.out.println("Encoding LSH...");
        int edges_compressed = 0;
        int supernode_count = 0;
        int[] S_copy = Arrays.copyOf(S, S.length);

        for (int i = 0; i < n; i++) {
            // if i is a supernode
            if (I[i] != -1) {
                int[] nodes_inside = Recover_S(i);
                TIntArrayList nodes_inside_list = new TIntArrayList();
                supernode_sizes[supernode_count] = nodes_inside.length;

                for (int j = 0; j < nodes_inside.length; j++) {
                    nodes_inside_list.add(nodes_inside[j]);
                    S_copy[nodes_inside[j]] = supernode_count;                
                }

                sn_to_n.put(supernode_count, nodes_inside_list);
                supernode_count++;
            }
        }

        LinkedList<FourTuple> edges_encoding = new LinkedList<FourTuple>();

        for (int node = 0; node < n; node++) {
            for(int neighbour : Gr.successorArray(node)) {
                if (S_copy[node] <= S_copy[neighbour]) {
                    edges_encoding.add(new FourTuple(S_copy[node], S_copy[neighbour], node, neighbour));
                }
            }
        }
        
        Collections.sort(edges_encoding);        

        int prev_A = edges_encoding.get(0).A;
        int prev_B = edges_encoding.get(0).B;
        HashSet<Pair<Integer, Integer>> edges_set = new HashSet<Pair<Integer, Integer>>();

        Iterator<FourTuple> iter = edges_encoding.iterator();
        while (!edges_encoding.isEmpty()) { 
            FourTuple e_encoding = edges_encoding.pop();  
            int A = e_encoding.A;
            int B = e_encoding.B;       
            
            if ((A != prev_A || B != prev_B)) { // we've moved onto a different pair of supernodes A and B

                if (prev_A <= prev_B) {
                    double edges_compare_cond = 0;
                    if (prev_A == prev_B) { edges_compare_cond = supernode_sizes[prev_A] * (supernode_sizes[prev_A] - 1) / 4; }
                    else                  { edges_compare_cond = (supernode_sizes[prev_A] * supernode_sizes[prev_B]) / 2;     }

                    if (edges_set.size() <= edges_compare_cond) {
                        if (prev_A != prev_B) edges_compressed += edges_set.size();
                        for (Pair<Integer, Integer> edge : edges_set) {
                            Cp_0.add(edge.getValue0());
                            Cp_1.add(edge.getValue1());
                        }                     
                    } else {
                        if (prev_A != prev_B) edges_compressed += supernode_sizes[prev_A] * supernode_sizes[prev_B] - edges_set.size() + 1;

                        P.add(new Pair(prev_A, prev_B));
                        
                        TIntArrayList in_A = sn_to_n.get(prev_A);
                        TIntArrayList in_B = sn_to_n.get(prev_B);
                        for (int a = 0; a < in_A.size(); a++) {
                            for (int b = 0; b < in_B.size(); b++) {
                                Pair<Integer, Integer> edge = new Pair(in_A.get(a), in_B.get(b));

                                if (!(edges_set.contains(edge))) {
                                    Cm_0.add(in_A.get(a));
                                    Cm_1.add(in_B.get(b));
                                }
                            } // for b
                        } // for a                         
                    } // else
                } // if
                
                edges_set = new HashSet<Pair<Integer, Integer>>();
            } // if            

            edges_set.add(new Pair(e_encoding.u, e_encoding.v));
            prev_A = A;
            prev_B = B;
        } // for edges encoding       
    }

    void drop() {
        System.out.println("Dropping...");

        for (int i = 0; i < n; i++) {
            cv[i] = error_bound * Gr.outdegree(i);
        }

        TIntArrayList updated_Cp_0 = new TIntArrayList();
        TIntArrayList updated_Cp_1 = new TIntArrayList();        

        for (int i = 0; i < Cp_0.size(); i++) {
            int edge_u = Cp_0.get(i);
            int edge_v = Cp_1.get(i);

            if (cv[edge_u] >= 1 && cv[edge_v] >= 1) {
                cv[edge_u] = cv[edge_u] - 1;
                cv[edge_v] = cv[edge_v] - 1;
            } else {
                updated_Cp_0.add(edge_u);
                updated_Cp_1.add(edge_v);
            }
        }
        Cp_0 = updated_Cp_0;
        Cp_1 = updated_Cp_1;
        
        TIntArrayList updated_Cm_0 = new TIntArrayList();
        TIntArrayList updated_Cm_1 = new TIntArrayList();   

        for (int i = 0; i < Cm_0.size(); i++) {
            int edge_u = Cm_0.get(i);
            int edge_v = Cm_1.get(i);

            if (cv[edge_u] >= 1 && cv[edge_v] >= 1) {
                cv[edge_u] = cv[edge_u] - 1;
                cv[edge_v] = cv[edge_v] - 1;
            } else {
                updated_Cm_0.add(edge_u);
                updated_Cm_1.add(edge_v);
            }
        }
        Cm_0 = updated_Cm_0;
        Cm_1 = updated_Cm_1;

        Collections.sort(P, new EdgeCompare(supernode_sizes));
        ArrayList<Pair<Integer, Integer>> updated_P = new ArrayList<Pair<Integer, Integer>>();
        for (Pair<Integer, Integer> edge : P) {
            int A = edge.getValue0();
            int B = edge.getValue1();

            if (A == B) { 
                updated_P.add(edge);
                continue; 
            }

            int size_B = supernode_sizes[B];
            boolean cond_A = true;
            TIntArrayList in_A = sn_to_n.get(A);

            for (int i = 0; i < in_A.size(); i++) {
                if (cv[in_A.get(i)] < size_B) {
                    cond_A = false;
                    break;
                }
            }
            if (!cond_A) { 
                updated_P.add(edge);
                continue; 
            }

            int size_A = supernode_sizes[A];
            boolean cond_B = true;
            TIntArrayList in_B = sn_to_n.get(B);

            for (int i = 0; i < in_B.size(); i++) {
                if (cv[in_B.get(i)] < size_A) {
                    cond_B = false;
                    break;
                }
            }
            if (!cond_B) { 
                updated_P.add(edge);
                continue; 
            }

            // if conditions are all true, ie (A != B && all v in A && all v in B)
            for (int i = 0; i < in_A.size(); i++) {
                cv[in_A.get(i)] = cv[in_A.get(i)] - size_B;
            }    
            for (int i = 0; i < in_B.size(); i++) {
                cv[in_B.get(i)] = cv[in_B.get(i)] - size_A;
            } 
            
        } 
        P = updated_P;   

        System.out.println("Drop Compression: " + (1 - (P.size() + Cp_0.size() + Cm_0.size() * 1.0)/(Gr.numArcs()/2 * 1.0)));
    }


    public void output_graph() {

        try {
            File directory = new File("compressed");
            if (!directory.exists()) {
                directory.mkdir();
            }
        } catch (Exception e) {
            System.out.println(e);
        }

        // Write graph supernodes
        try {
            FileWriter fileWriterG = new FileWriter("compressed/G.txt");

            int supernode_count = sn_to_n.size();
            for (int supernode = 0; supernode < supernode_count; supernode++) {
                fileWriterG.write(supernode + "\t"); 

                TIntArrayList in_supernode = sn_to_n.get(supernode);
                for (int node = 0; node < in_supernode.size(); node++) {
                    fileWriterG.write("" + in_supernode.get(node));

                    if (node != in_supernode.size() - 1) {
                        fileWriterG.write("\t");
                    }
                }  

                fileWriterG.write("\n");
            }

            fileWriterG.close();
        } catch (Exception e) {
            System.out.println(e);
        }

        // Write Edge List P and Correct Sets
        try {
            FileWriter fileWriterP = new FileWriter("compressed/P.txt");
            for (Pair<Integer, Integer> edge : P) {
                fileWriterP.write(edge.getValue0() + "\t" + edge.getValue1() + "\n");
            }
            fileWriterP.close();

            FileWriter fileWriterCp = new FileWriter("compressed/Cp.txt");

            for (int i = 0; i < Cp_0.size(); i++) {
                fileWriterCp.write(Cp_0.get(i) + "\t" + Cp_1.get(i) + "\n");
            }
            fileWriterCp.close();

            FileWriter fileWriterCm = new FileWriter("compressed/Cm.txt");
            for (int i = 0; i < Cm_0.size(); i++) {
                fileWriterCm.write(Cm_0.get(i) + "\t" + Cm_1.get(i) + "\n");
            }
            fileWriterCm.close();
        } catch (Exception e) {
            System.out.println(e);
        } 
    }

	//==================== Creating w_a for each suprenodes inside group Q
	HashMap<Integer, HashMap<Integer, Integer>> create_W(int[] Q, int group_size){

		HashMap<Integer, HashMap<Integer, Integer>> w_All = new HashMap<Integer, HashMap<Integer, Integer>>();

		for (int i = 0 ; i < group_size ; i++){
			HashMap<Integer, Integer> w_Single = new HashMap<Integer, Integer>();
			int[] Nodes = Recover_S(Q[i]);

			for (int j = 0 ; j < Nodes.length ; j++){
				int[] Neigh = Gr.successorArray(Nodes[j]);

				for (int k = 0 ; k < Neigh.length ; k++){
					if (w_Single.containsKey(Neigh[k]))
						w_Single.put(Neigh[k], w_Single.get(Neigh[k])+1);
					else
						w_Single.put(Neigh[k], 1);
				}
			}
			w_All.put(i,w_Single);
		}

		return w_All;
    }
    
    // spA, spB are W_A, W_B respectively
	double costSaving(HashMap<Integer,Integer> spA , HashMap<Integer,Integer> spB , int spA_id , int spB_id){
		int[] nodesA = Recover_S(spA_id);
		int[] nodesB = Recover_S(spB_id);
		double costA = 0,costB = 0 ,costAunionB = 0;
		HashMap<Integer,Integer> candidateSize  = new HashMap<Integer,Integer>(); // supernode id S to the number of nodes inside S
		HashMap<Integer,Integer> candidate_spA  = new HashMap<Integer,Integer>(); // supdnode id S to number of edges from A to all ndoes in S
        HashMap<Integer,Integer> candidate_spB  = new HashMap<Integer,Integer>(); // supdnode id S to number of edges from B to all ndoes in S
        
		for (Integer key : spA.keySet()){
			if (!candidateSize.containsKey(S[key])) {
				int[] nodes = Recover_S(S[key]);
				candidateSize.put(S[key] , nodes.length);
				candidate_spA.put(S[key] , spA.get(key));
			} else {
                candidate_spA.put(S[key] , candidate_spA.get(S[key]) + spA.get(key));
            }
        }
		for (Integer key : spB.keySet()){
			if (!candidateSize.containsKey(S[key])) {
				int[] nodes = Recover_S(S[key]);
				candidateSize.put(S[key] , nodes.length);
				candidate_spB.put(S[key] , spB.get(key));
			} else if(candidate_spB.containsKey(S[key])) {
				candidate_spB.put(S[key] , candidate_spB.get(S[key]) + spB.get(key));
            } else {
				candidate_spB.put(S[key] , spB.get(key));
            }
		}
		
		//Start Calculating costA,costB & costAunionB
		for (Integer key : candidate_spA.keySet()){
			if (key == spA_id) { // in case of superloop
				if (candidate_spA.get(key) >= (nodesA.length * 1.0 * nodesA.length)/4.0)
					costA += (nodesA.length * nodesA.length) - candidate_spA.get(key) ;
				else
					costA += candidate_spA.get(key);
				continue;
			}
			if (candidate_spA.get(key) >= (nodesA.length * 1.0 * candidateSize.get(key))/2.0)
				costA += (candidateSize.get(key) * nodesA.length) - candidate_spA.get(key) + 1;
			else 
				 costA += candidate_spA.get(key);
			
			if (key==spB_id)
				continue;

			if (candidate_spB.containsKey(key)){
				if ((candidate_spA.get(key) + candidate_spB.get(key)) >= ((nodesA.length + nodesB.length) * 1.0 * candidateSize.get(key))/2.0)
					costAunionB += (candidateSize.get(key) * (nodesA.length + nodesB.length)) - candidate_spA.get(key) - candidate_spB.get(key) + 1;
			    else 
				    costAunionB += candidate_spA.get(key) + candidate_spB.get(key);
			} else {
				if ((candidate_spA.get(key) >= ((nodesA.length + nodesB.length)* 1.0 *candidateSize.get(key))/2.0))
					costAunionB += (candidateSize.get(key)  *(nodesA.length + nodesB.length)) - candidate_spA.get(key) + 1;
				else 
					costAunionB += candidate_spA.get(key);
			}
		}
		for (Integer key:candidate_spB.keySet()){

			if (key== spB_id){ // in case of superloop
				if (candidate_spB.get(key) >= (nodesB.length * 1.0 * nodesB.length)/4.0)
					costB += (nodesB.length * nodesB.length) - candidate_spB.get(key) ;
				else
					costB += candidate_spB.get(key);
				continue;
			}

			if (candidate_spB.get(key) >= (nodesB.length * 1.0 * candidateSize.get(key))/2.0)
				costB += (candidateSize.get(key) * nodesB.length) - candidate_spB.get(key) + 1;
			else 
                costB += candidate_spB.get(key);
                
			if (candidate_spA.containsKey(key) || key == spA_id){  
				continue;
			} else {
				if ((candidate_spB.get(key) >= ((nodesA.length + nodesB.length)* 1.0 * candidateSize.get(key))/2))
					costAunionB += (candidateSize.get(key) * (nodesA.length + nodesB.length)) - candidate_spB.get(key) + 1;
				else 
					costAunionB += candidate_spB.get(key);
			}
        }
        
		int aUnionBEdges = 0;
		if (candidate_spA.containsKey(spA_id))   //Superloop between spA and spA
			aUnionBEdges += candidate_spA.get(spA_id);
		if (candidate_spA.containsKey(spB_id)) // Superloop between spA and spB
			aUnionBEdges += candidate_spA.get(spB_id);
		if (candidate_spB.containsKey(spB_id)) // Superloop between spB and spB
			aUnionBEdges += candidate_spB.get(spB_id);
		if (aUnionBEdges > 0){
			if (aUnionBEdges >= ((nodesA.length + nodesB.length) * 1.0 * (nodesA.length + nodesB.length))/4.0)
				costAunionB += (nodesA.length + nodesB.length) * (nodesA.length + nodesB.length) - aUnionBEdges;
			else
				costAunionB += aUnionBEdges;
        }
        
		return 1 - (costAunionB )/(costA + costB);
	}

	//======================= HashMap<Integer,Integer> update_w after merging
	HashMap<Integer,Integer> update_W(HashMap<Integer,Integer> w_A , HashMap<Integer,Integer> w_B)
	{
		HashMap<Integer,Integer> result = new HashMap<Integer,Integer>();
		for(Integer key: w_A.keySet()){
			if (w_B.containsKey(key))
				result.put(key, w_A.get(key) + w_B.get(key));
			else
				result.put(key, w_A.get(key));
		}
		for(Integer key: w_B.keySet()){
			if (w_A.containsKey(key))
				continue;
			result.put(key, w_B.get(key));
		}
		return result;
	}
	//==================== Jaccard Similarity (Eq.4) and Savings Eq 3 and Eq. 5
	// Updated version of Jaccard Sim with using just pair of W's
    //====================================================================
    double[] JacSim_SavCost(HashMap<Integer, Integer> w_A , HashMap<Integer,Integer> w_B){
        int  down =0; int up = 0; int savings_up = 0; int savings_down = 0;
        savings_down = w_A.size() + w_B.size();
        double[] res = new double[2];
        for (Integer key : w_A.keySet()) {
            savings_up++;
            if (w_B.containsKey(key)) {
                if (w_A.get(key)<=w_B.get(key))
                {
                    up = up + w_A.get(key);	
                    down = down + w_B.get(key);	
                }
                else
                {
                    down = down + w_A.get(key);	
                    up = up + w_B.get(key);	
                }
            }
            else
                down = down + w_A.get(key);	
        }
        for (Integer key: w_B.keySet()){
            if (!(w_A.containsKey(key))){
                savings_up++;
                down = down + w_B.get(key);	
            }
        }
        res[0] = (up*1.0)/(down*1.0);
        res[1] = 1 - (savings_up * 1.0)/(savings_down * 1.0) ;
        return res;
    }
    //=============== Realize the number of duplicate value (key) from the start postion (st_pos) in arr
    int groups_length(OnePermHashSig[] arr, OnePermHashSig key, int st_pos) {
        int count = 1;
        while(arr[st_pos++].equals(key) && st_pos<arr.length - 1) count++;
        return count;
    }
    //====================== Finds the nodes in each supernode with index key
	int[] Recover_S(int key){
		//Extracting the nodes belong to supernode key and return it (Arr)
		int length1 = supernode_length(key);
		int[] Arr = new int[length1];
		int counter = 0;
		int kk1 = I[key];
		while (kk1 !=-1){
			Arr[counter++] = kk1;
			kk1 = J[kk1];
		}
		return Arr;
	}
	//=================================================================== Find the length of supernodes with index SP_A
	int supernode_length(int SP_A){
		int counter = 0;
		int kk = I[SP_A];
		while(kk!=-1){
			counter++;
			kk = J[kk];
		}
		return counter;
	}
	//========================================================== Update Merging Supernodes
	void Update_S(int A, int B){
		int[] A_Nodes = Recover_S(A);
		int[] B_Nodes = Recover_S(B);
		J[A_Nodes[A_Nodes.length-1]] = I[B];
		I[B] = -1;
		for (int i = 0 ; i < A_Nodes.length ; i++) S[A_Nodes[i]] = I[A];
			for (int i = 0 ; i < B_Nodes.length ; i++) S[B_Nodes[i]] = I[A];
	}


	void test(int Iter) {
        System.out.println("----------------------------------- LSH MERGE ----------------------------------------");

        long divideStartTime = System.currentTimeMillis();
        long encodeStartTime = 0;
        long dropStartTime = 0;

        int k_bins = signatureLength;

		for (int iter = 1 ; iter <=Iter ; iter++){
            System.out.print(iter + " ");

            DivideNew(k_bins);

			int cnt_groups = 0;
            OnePermHashSig g = new OnePermHashSig(k_bins);
			for (int i = gstart; i < n; i++) {              
                if (!F_OPH[G[i]].equals(g)) {
					cnt_groups++;
					g = F_OPH[G[i]];
				}
            }

            OnePermHashSig[] group_prop_0 = new OnePermHashSig[cnt_groups]; // when F[G[i]]>F[G[i-1]] -> Another new group. 
            int[] group_prop_1 = new int[cnt_groups];

			// In this case: F[G[i]]: id of the new detected group  -> group_prop[counter][0] <- F[G[i]]
            // The first hit of the group in F[G[]]  is i -> group_prop[counter][1] <- i
            g = new OnePermHashSig(k_bins + 1);
			int counter = 0;
			for (int i = gstart; i < n; i++) {
                if (!F_OPH[G[i]].equals(g)) {
					group_prop_0[counter] = F_OPH[G[i]];
					group_prop_1[counter] = i;
					counter++;
					g = F_OPH[G[i]];
                }
			}

			merge(cnt_groups, iter, group_prop_0, group_prop_1);
            
            if (iter % print_iteration_offset == 0 && iter != Iter) {
                System.out.println("\n------------------------- ITERATION " + iter);
                divideAndMergeTime += (System.currentTimeMillis() - divideStartTime) / 1000.0;
                System.out.println("Divide and Merge Time: " + divideAndMergeTime + " seconds");

                supernode_sizes = new int[n];
                sn_to_n = new HashMap<Integer, TIntArrayList>();
                P = new ArrayList<Pair<Integer, Integer>>();
                Cp_0 = new TIntArrayList(); Cp_1 = new TIntArrayList();
                Cm_0 = new TIntArrayList(); Cm_1 = new TIntArrayList();

                encodeStartTime = System.currentTimeMillis();
                encode_lsh();
                encodeTime = (System.currentTimeMillis() - encodeStartTime) / 1000.0;
                System.out.println("Encode Time: " + encodeTime + " seconds");

                dropStartTime = System.currentTimeMillis();
                if (error_bound > 0) {
                    drop();
                }
                dropTime = (System.currentTimeMillis() - dropStartTime) / 1000.0;
                System.out.println("Drop Time: " + dropTime + " seconds\n");

                divideStartTime = System.currentTimeMillis();
            }

        } // for	

        System.out.println("\n------------------------- FINAL (Iteration " + Iter + ")");
        divideAndMergeTime += (System.currentTimeMillis() - divideStartTime) / 1000.0;
        System.out.println("Divide and Merge Time: " + divideAndMergeTime + " seconds");

        supernode_sizes = new int[n];
        sn_to_n = new HashMap<Integer, TIntArrayList>();
        P = new ArrayList<Pair<Integer, Integer>>();
        Cp_0 = new TIntArrayList(); Cp_1 = new TIntArrayList();
        Cm_0 = new TIntArrayList(); Cm_1 = new TIntArrayList();

        encodeStartTime = System.currentTimeMillis(); 
        encode_lsh(); 
        encodeTime = (System.currentTimeMillis() - encodeStartTime) / 1000.0;
        System.out.println("Encode Time: " + encodeTime + " seconds");

        dropStartTime = System.currentTimeMillis();
        if (error_bound > 0) {
            drop();
        }
        dropTime = (System.currentTimeMillis() - dropStartTime) / 1000.0;
        System.out.println("Drop Time: " + dropTime + " seconds");

	}

	void evaluateCompression() {
        int sp_num = 0;
        for (int i = 0; i < n; i++) {
            if (I[i] != -1) { sp_num++; }
        }
        System.out.println("Number of Supernodes: " + sp_num);
        System.out.println("Number of Edges Compressed: " + (P.size() + Cp_0.size() + Cm_0.size()));
        System.out.println("P edges: " + P.size());
        System.out.println("Cp edges: " + Cp_0.size());
        System.out.println("Cm edges: " + Cm_0.size());
        System.out.println("Number of Edges Original:   " + Gr.numArcs() / 2);
        System.out.println("Compression: " + (1 - (P.size() + Cp_0.size() + Cm_0.size() * 1.0)/(Gr.numArcs() / 2 * 1.0)));
	}

	public static void main(String[] args) throws Exception {
        int iteration = Integer.parseInt(args[1]);
        double error_bound = 0;
		String basename = args[0];
		long startTime = System.currentTimeMillis();
		Ldme t = new Ldme(basename, error_bound);
        t.print_iteration_offset = Integer.parseInt(args[2]);
        t.signatureLength = Integer.parseInt(args[3]);
        t.test(iteration);

        System.out.println();
        System.out.println("----------------------------------- EVALUATION ----------------------------------------");
        t.evaluateCompression();
        
        System.out.println();
        System.out.println("----------------------------------- OUTPUT ----------------------------------------");
        t.output_graph();
	}
}