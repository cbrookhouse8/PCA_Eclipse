package visible.objects;

import processing.core.PApplet;
import small.data.structures.Matrix;

public class Axes {

	public Matrix vcs;
	PApplet p;

	public Axes(PApplet p) {
		this.p = p;
		vcs = new Matrix(4, 4);
		for (int i = 3; i < 3 * 4; i += 4)
			vcs.M[i] = 1;
	}

	public void display(Matrix R, Matrix transforms, Matrix display) {

		Matrix vcsCopy = new Matrix(4, 3);
		Matrix origin = new Matrix(new float[] { 0, 0, 0, 1 }, 4, 1);

		for (int i = 0; i < 12; i++)
			vcsCopy.M[i] = vcs.M[i];

		vcsCopy.mult(R).mult(transforms).project(2).mult(display);
		origin.mult(transforms).project(2).mult(display);
		
		// p.textAlign(CENTER,CENTER);
		p.stroke(255, 0, 0); // x
		p.strokeWeight(2);
		p.fill(255, 0, 0);
		p.line(origin.M[0], origin.M[1], vcsCopy.M[0], vcsCopy.M[1]);
		p.text("X", vcsCopy.M[0], vcsCopy.M[1]);
		p.stroke(0, 0, 255); // z
		p.fill(0, 0, 255);
		p.line(origin.M[0], origin.M[1], vcsCopy.M[8], vcsCopy.M[9]);
		p.text("Z", vcsCopy.M[8], vcsCopy.M[9]);
		p.stroke(0, 255, 0); // y
		p.fill(0, 255, 0);
		p.line(origin.M[0], origin.M[1], vcsCopy.M[4], vcsCopy.M[5]);
		p.text("Y", vcsCopy.M[4], vcsCopy.M[5]);
		p.strokeWeight(1);
	}

}