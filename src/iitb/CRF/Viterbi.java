package iitb.CRF;

import java.lang.*;
import java.io.*;
import java.util.*;

import cern.colt.matrix.*;
import cern.colt.matrix.impl.*;
/**
 *
 * Viterbi search
 *
 * @author Sunita Sarawagi
 *
 */ 


class Soln {
    double score=-1*Double.MAX_VALUE;
    Soln prevSoln=null;
    int label = -1;
    int pos;
	    
    Soln(int id, int p) {label = id;pos = p;}
    void clear() {
	score=-1*Double.MAX_VALUE;
	prevSoln=null;
    }
    boolean isClear() {
	return (score == -1*Double.MAX_VALUE);
    }
    void copy(Soln soln) {
	score = soln.score;
	prevSoln = soln.prevSoln;
    }
    int prevPos() {
	return (prevSoln == null)?-1:prevSoln.pos;
    }
    int prevLabel() {
	return (prevSoln == null)?-1:prevSoln.label;
    }
    boolean equals(Soln s) {
	return (label == s.label) && (pos == s.pos) && (prevPos() == s.prevPos()) && (prevLabel() == s.prevLabel());
    }
};

class Viterbi {
    CRF model;
    int beamsize;
    Viterbi(CRF model, int bs) {
	this.model = model;
	beamsize = bs;
    }
    class Entry {
	Soln solns[];
	Entry(int beamsize, int id, int pos) {
	    solns = new Soln[beamsize];
	    for (int i = 0; i < solns.length; i++)
		solns[i] = new Soln(id, pos);
	}
	void clear() {
	    for (int i = 0; i < solns.length; i++)
		solns[i].clear();
	}
	int size() {return solns.length;}
	Soln get(int i) {return solns[i];}
	void insert(int i, double score, Soln prev) {
	    for (int k = size()-1; k > i; k--) {
		solns[k].copy(solns[k-1]);
	    }
	    solns[i].score = score;
	    solns[i].prevSoln = prev;
	}
	void add(Entry e, double thisScore) {
	    int insertPos = 0;
	    for (int i = 0; (i < e.size()) && (insertPos < size()); i++) {
		double score = e.get(i).score + thisScore;
		insertPos = findInsert(insertPos, score, e.get(i));
	    }
	    //	    print();
	}
	int findInsert(int insertPos, double score, Soln prev) {
	    for (; insertPos < size(); insertPos++) {
		if (score > get(insertPos).score) {
		    //	    System.out.println("inserted " + insertPos + " score " + score);
		    insert(insertPos, score, prev);
		    insertPos++;
		    break;
		}
	    }
	    return insertPos;
	}
	void add(double thisScore) {
	    findInsert(0, thisScore, null);
	}
	int numSolns() {
	    for (int i = 0; i < solns.length; i++)
		if (solns[i].isClear())
		    return i+1;
	    return size();
	}
	void print() {
	    String str = "";
	    for (int i = 0; i < size(); i++)
		str += ("["+i + " " + solns[i].score + " i:" + solns[i].pos + " y:" + solns[i].label+"]");
	    System.out.println(str);
	}
    };
    Entry winningLabel[][];
    Entry finalSoln;
    DenseDoubleMatrix2D Mi;
    DenseDoubleMatrix1D Ri;

    void allocateScratch(int numY) {
	Mi = new DenseDoubleMatrix2D(numY,numY);
	Ri = new DenseDoubleMatrix1D(numY);
	winningLabel = new Entry[numY][];
	finalSoln = new Entry(beamsize,0,0);
    }
    double fillArray(DataSequence dataSeq, double lambda[], boolean calcScore) {
	double corrScore = 0;
	int numY = model.numY;
	for (int i = 0; i < dataSeq.length(); i++) {
	    // compute Mi.
	    Trainer.computeLogMi(model.featureGenerator,lambda,dataSeq,i,Mi,Ri,false);
	    for (int yi = 0; yi < numY; yi++) {
		winningLabel[yi][i].clear();
	    }
	    for (int yi = model.edgeGen.firstY(i); yi < numY; yi = model.edgeGen.nextY(yi,i)) {
		if (i > 0) {
		    for (int yp = model.edgeGen.first(yi); yp < numY; yp = model.edgeGen.next(yi,yp)) {
			double val = Mi.get(yp,yi)+Ri.get(yi);
			winningLabel[yi][i].add(winningLabel[yp][i-1], val);
		    }
		} else {
		    winningLabel[yi][i].add(Ri.get(yi));
		}
	    }
	    if (calcScore)
		corrScore += (Ri.get(dataSeq.y(i)) + ((i > 0)?Mi.get(dataSeq.y(i-1),dataSeq.y(i)):0));
	}
	return corrScore;
    }
    
    public void bestLabelSequence(DataSequence dataSeq, double lambda[]) {
	viterbiSearch(dataSeq, lambda,false);
	Soln ybest = finalSoln.get(0);
	ybest = ybest.prevSoln;
	while (ybest != null) {
	    dataSeq.set_y(ybest.pos, ybest.label);
	    ybest = ybest.prevSoln;
	}
    }
    public double viterbiSearch(DataSequence dataSeq, double lambda[], boolean calcCorrectScore) {
	if (Mi == null) {
	    allocateScratch(model.numY);
	}
	if ((winningLabel[0] == null) || (winningLabel[0].length < dataSeq.length())) {
	    for (int yi = 0; yi < winningLabel.length; yi++) {
		winningLabel[yi] = new Entry[dataSeq.length()];
		for (int l = 0; l < dataSeq.length(); l++)
		    winningLabel[yi][l] = new Entry((l==0)?1:beamsize, yi, l);
	    }
	}
	
	double corrScore = fillArray(dataSeq, lambda,calcCorrectScore);

	finalSoln.clear();
	for (int yi = 0; yi < model.numY; yi++) {
	    finalSoln.add(winningLabel[yi][dataSeq.length()-1], 0);
	}
	return corrScore;
    }
    int numSolutions() {return finalSoln.numSolns();}
    Soln getBestSoln(int k) {
	return finalSoln.get(k).prevSoln;
    }
};