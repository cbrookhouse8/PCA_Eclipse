import manipulation.Dragger;
import manipulation.PositionTool;
import processing.core.PApplet;
import processing.core.PVector;
import small.data.structures.Matrix;
import small.data.structures.Quaternion;
import utilities.Logger;
import visible.objects.Axes;
import visible.objects.Ellipsoid;
import visible.objects.Grid;

/**
 * TODO: handle the offsets properly
 */
public class PCA extends PApplet {

	Logger log;

	Grid g; 		// Nb. transformed to world space within the class
	Dragger d; 	// Object that handles changes in camera orientation due to mouse drag
	Axes world;

	Matrix camera, camRot, camTrans, cameraToWorld;
	Matrix camArm, camPivot;

	Matrix worldToCamera, toDisplay;

	Matrix R; // orientation of data's basis before/after PCA

	Matrix localAxes;

	Matrix data; 	// aim for a square number of points
	Matrix PCS; 		// just the principal components
	Matrix all; 		// PCA's and eigenvalues in col 4

	boolean axesFound;
	Quaternion q1, q2;

	int count; 		// Used in Animation Operations 1 to 4
	int count2; 		// Used in Animation Operation 5
	boolean movingBasis; 	// read as 'moving the existing data basis onto the
							// basis given by PCA'

	Ellipsoid container;

	// float count;
	boolean orbiting;

	boolean ellipsoidFound;
	boolean showEllipsoid;

	boolean showGrid;

	PositionTool alignCamera;
	boolean aligning;
	boolean aligned;
	
	// identifies the principal component to which
	// the camera's look-at vector is aligned
	int alignedTo; 

	Matrix fromRotArm;
	
	// represents an animated rotation of the camera arm
	// read as 'aligns the camera arm'
	Quaternion alArm; 

	boolean downProjecting, downProjected;
	boolean recovering;
	
	// read as 'variance being animated'
	boolean varAnimating;
	
	float[] eigenVals;

	PVector butp1, butp2, butp3; // 0,1,2
	PVector projBut; 	// 3
	PVector orbBut; 		// 4
	PVector ellipBut; 	// 5
	PVector newData; 	// 6
	PVector gridBut; 	// 7

	int mouseCount;

	// stores the x,y coordinates of the axis
	// capturing greatest variance after data
	// has been projected down to 2D. Needs to
	// be global because the line is animated.
	Matrix vari2D; 

	// Run this project as Java application and this
	// method will launch the sketch
	public static void main(String[] args) {
		PApplet.main("PCA");
	}

	public void settings() {
		size(600, 600);
	}

	public void setup() {

		log = new Logger(this);
		background(0);
		
		world = new Axes(this);
		g = new Grid(this);
		d = new Dragger();

		camera = new Matrix(4, 3); // camera's basis vectors
		camera.M[3] = 1;
		camera.M[7] = 1;
		camera.M[11] = 1;

		camRot = new Matrix(4, 4);
		camTrans = new Matrix(4, 4);

		camArm = new Matrix(new float[] { 0, 0, 6 }, 3, 1);
		camPivot = new Matrix(new float[] { 0, 1, 0 }, 3, 1);

		// translation vector of the camera from world-space origin
		Matrix buildTrans = camArm.matrixAdd(camArm, camPivot);

		// assign this vector as the fourth col of 4x4 camTrans matrix
		camTrans.M[12] = buildTrans.M[0];
		camTrans.M[13] = buildTrans.M[1];
		camTrans.M[14] = buildTrans.M[2];

		// need to clarify signs- left vs right handed coordinate system

		buildTrans.normalize();
		Matrix jVec = buildTrans.cross(new Matrix(new float[] { -1, 0, 0 }, 3, 1));
		jVec.normalize();

		// construct the matrix that describes the camera's orientation align
		// the camera's look-at vector (3rd col) with the translation vector
		camRot.M[4] = jVec.M[0];
		camRot.M[5] = jVec.M[1];
		camRot.M[6] = jVec.M[2];
		camRot.M[8] = -buildTrans.M[0];
		camRot.M[9] = -buildTrans.M[1];
		camRot.M[10] = -buildTrans.M[2];

		// Product of 4x4 Translation Matrix and 4x4 Rotation Matrix
		cameraToWorld = camera.matrixProduct(camTrans, camRot);
		worldToCamera = cameraToWorld.getInverse();

		toDisplay = new Matrix(4, 4);
		toDisplay.M[0] = 500;
		toDisplay.M[5] = 500;
		toDisplay.M[12] = width / 2;
		toDisplay.M[13] = height / 2;

		stroke(255);
		initializeButtons();
		R = new Matrix(4, 4);

		createPoints();

		axesFound = false;
		ellipsoidFound = false;
		showEllipsoid = false;
		showGrid = true;
		aligning = aligned = false;

		downProjecting = false;
		varAnimating = false;

		mouseCount = 0;
		alignedTo = -1;
		count2 = 0;

	}

	public void draw() {
		// redraw the background
		background(0);
		
		textAlign(LEFT, CENTER);
		fill(255);
		text("count = " + count, 50, 50);
		noFill();
		
		// check if dragging the camera with mouse
		if (d.check(pmouseX, pmouseY, mouseX, mouseY)) { 
			Matrix camera2 = camera.matrixProduct(camRot, camera.getCopy());
			Matrix rotUpdate = d.reorientCamera(camera2);
			camRot.mult(rotUpdate); // update camera's orientation
		}

		// ANIMATION OPERATION 1

		// If orbiting the data, update camera's orientation and position
		if (orbiting && !aligning) {
			if (count < 80) {
				Quaternion spin = new Quaternion(dtEase(80, count), 0, -1, 0);

				camRot.mult(spin.getR(4));
				camArm.mult(spin.getR(3));
				camPivot.mult(spin.getR(3)); // may not be necessary depending on the pivot

				camTrans.M[12] = camArm.M[0] + camPivot.M[0];
				camTrans.M[13] = camArm.M[1] + camPivot.M[1];
				camTrans.M[14] = camArm.M[2] + camPivot.M[2];

				count++;
			} else {
				orbiting = false;
				count = 0;
			}
		}

		// ANIMATION OPERATION 2

		// If aligning the camera and its look-at with one
		// of the principal components of the data
		if (aligning) {
			float t = 0.5f - cos(count * PI / 80) / 2;
			Quaternion utilQ = new Quaternion(0, 0, 1, 0); // utility quaternion

			// Quaternion corresponding to an interpolation along
			// the full arc of rotation for the arm
			Quaternion armInterp = utilQ.interpolate(t, utilQ, alArm);

			// the position of the arm before the user initiated
			// the alignment process
			camArm = fromRotArm.getCopy();

			// update arm position
			camArm.mult(armInterp.getR(3));

			// the new translation vector describing the camera's
			// position in relation to the origin
			camTrans.M[12] = camArm.M[0] + camPivot.M[0];
			camTrans.M[13] = camArm.M[1] + camPivot.M[1];
			camTrans.M[14] = camArm.M[2] + camPivot.M[2];

			// now update the camera's orientation. Crucially, the
			// look-at must point at the origin and the camera remain
			// aligned with floor (plane) of world-space
			Matrix camBasis = new Matrix(4, 4);

			// K basis vector (look at vector)
			// just the normalized tranlsation vector
			Matrix newLookAt = new Matrix(3, 1);

			newLookAt.M[0] = -camTrans.M[12];
			newLookAt.M[1] = -camTrans.M[13];
			newLookAt.M[2] = -camTrans.M[14];

			newLookAt.normalize();

			// I basis vector
			Matrix iVec = newLookAt.cross(camBasis.isolate(1, 2, 3, 1));
			iVec.normalize();

			// J basis
			Matrix jVec = newLookAt.cross(iVec);
			jVec.normalize();

			// assemble the new camBasis. It is the rotation matrix for the
			// initial camera whose basis vectors are just the identity matrix
			camBasis.M[0] = iVec.M[0];
			camBasis.M[1] = iVec.M[1];
			camBasis.M[2] = iVec.M[2];
			camBasis.M[4] = jVec.M[0];
			camBasis.M[5] = jVec.M[1];
			camBasis.M[6] = jVec.M[2];
			camBasis.M[8] = newLookAt.M[0];
			camBasis.M[9] = newLookAt.M[1];
			camBasis.M[10] = newLookAt.M[2];

			camRot = camBasis.getCopy();

			// check if the aligmnet process is complete
			if (count == 80) {
				aligned = true;
				aligning = false;
				camArm = fromRotArm.mult(alArm.getR(3));
				count = 0;
			} else {
				count++;
			}
		}

		// ANIMATION OPERATION 3

		// If animating the rotation of the standard basis onto
		// the basis given by PCA

		if (movingBasis) {
			if (count < 80) { // Nb. this counter is incremented by Animation op 2
				float ease = 0.5f - cos(count * PI / 80) / 2;
				q1 = new Quaternion(0, 0, 1, 0);
				Quaternion intermediate = q1.interpolate(ease, q2);
				R = intermediate.getR(4);
			} else {
				for (int j = 0; j < 3; j++) {
					for (int i = 0; i < 3; i++)
						world.vcs.M[j * 4 + i] = PCS.M[j * 3 + i];
				}
				R = new Matrix(4, 4);
				movingBasis = false;
			}
		}

		// ---- CALCULATE worldToCamera matrix ----

		// perhaps too many unnecessary multiplications here
		cameraToWorld = camera.matrixProduct(camTrans, camRot);
		worldToCamera = cameraToWorld.getInverse();

		// ----------------------------------------

		// ANIMATION OPERATION 4
		// If animating the projection of the data onto plane

		if (showGrid) {
			if ((!downProjecting && !downProjected) && !recovering) {
				g.display(worldToCamera, toDisplay, 60);
			} else if (downProjecting) {
				g.display(worldToCamera, toDisplay, count);
			} else if (recovering) {
				g.display(worldToCamera, toDisplay, count);
			}
		}

		// ---------- \\

		world.display(R, worldToCamera, toDisplay);
		showData();

		// ---------- //

		// ANIMATION OPERATION 5

		// If animating (drawing) the line (axis) along the
		// principal component corresponding to the greatest
		// eigenvalue (variance) of the projected data.

		if (downProjected) {
			if (count2 == 0)
				varAnimating = true;

			if (varAnimating) {
				if (count2 == 60) {
					varAnimating = false;
					extentAnimation(1);
				} else {
					count2++;
					extentAnimation(0.5f - 0.5f * cos(PI * count2 / 60));
				}
			} else {
				extentAnimation(1);
			}
		}

		// ---------- \\

		// Display ellipsoidal bounding volume
		if (showEllipsoid && axesFound)
			container.display(worldToCamera, toDisplay);

		// Display buttons and mouse
		showButtons();
		showMouse();

		// ---------- //

	} // end of draw()

	void showData() {
		Matrix p1;
		float dia;
		int intensity = 255;
		Matrix viewDist = camTrans.matrixAdd(camArm, camPivot);

		for (int j = 0; j < data.m; j++) {
			p1 = new Matrix(new float[] { data.M[j * 3], data.M[j * 3 + 1], data.M[j * 3 + 2], 1 }, 4, 1);

			fill(map(round(data.M[j * 3]), -2, 2, 0, 255), map(round(data.M[j * 3 + 1]), -2, 2, 0, 255),
					map(round(data.M[j * 3 + 2]), -2, 2, 0, 255), intensity);

			p1.mult(worldToCamera);

			if ((downProjecting || recovering) && !downProjected) {
				float offset = p1.M[2] - viewDist.mag();
				p1.M[2] = viewDist.mag() + offset * count / 60;
			} else if (downProjected) {
				p1.M[2] = viewDist.mag();
			}

			dia = (0.07f / p1.M[2]) * 500;

			p1.project(2).mult(toDisplay);
			noStroke();
			ellipse(p1.M[0], p1.M[1], dia, dia);
		}

		if (downProjecting) {
			count--;
			if (count < 0) {
				downProjecting = false;
				downProjected = true;
			}
		} else if (recovering) {
			count++;
			if (count > 60) {
				recovering = false;
				downProjected = downProjecting = false;
				count = 0;
				count2 = 0;
			}
		}
	}

	Matrix findPlanarComponents() {
		// the two principal components to which the camera's
		// look-at is not aligned
		Matrix planePCS = new Matrix(4, 2);
		
		int targCol = 0;
		
		// worth storing the respective eigenvalues of the
		// two principal components in case we want to output
		// info about data's variance along those axes
		eigenVals = new float[2]; 

		for (int j = 0; j < 3; j++) {
			// we want the two principal components that are orthogonal to
			// the camera's look-at vector. Since the camera is already aligned
			// to one principal component, we want j != alignedTo
			if (j != alignedTo) {
				for (int i = 0; i < 3; i++)
					planePCS.M[targCol * 4 + i] = all.M[j * 3 + i];
				planePCS.M[targCol * 4 + 3] = 1; // for translation, although not relevant in this special case
				eigenVals[targCol] = all.M[9 + j];
				targCol++;
			}
		}

		// the two principal components effectively become 2D
		// (because look-at is orthogonal to them)
		planePCS.mult(worldToCamera).project(2);

		Matrix viewDist = camTrans.matrixAdd(camArm, camPivot);

		// normalize the 2D pc that captures more variance
		// (greater corresponding eigenvalue)
		Matrix main = new Matrix(new float[] { planePCS.M[0], planePCS.M[1] }, 2, 1);
		main.normalize();

		float dp;
		float projectionMax = 0;
		float projectionMin = 0;

		Matrix p1;

		// find the two points whose projections onto the principal
		// component of greater corresponding eigenvalue (variance)
		// produce the data's extents (similar procedure used to
		// calculate bounding volume construction)
		for (int j = 0; j < data.m; j++) {
			p1 = new Matrix(new float[] { data.M[j * 3], data.M[j * 3 + 1], data.M[j * 3 + 2], 1 }, 4, 1);

			p1.mult(worldToCamera);
			p1.M[2] = viewDist.mag();
			p1.project(2);

			Matrix p1i = p1.isolate(1, 1, 2, 1); // effectively 2D

			dp = p1i.dot(main);

			if (dp > projectionMax)
				projectionMax = dp;
			if (dp < projectionMin)
				projectionMin = dp;
		}

		println("projMax " + projectionMax);
		println("projMin " + projectionMin);

		Matrix rval = new Matrix(2, 2);
		// scale to the display before returning
		// scale the 2D principal component (of greater variance) by
		// the max and min projections. Then scale for the display.

		rval.M[0] = main.M[0] * projectionMin * toDisplay.M[0];
		rval.M[1] = main.M[1] * projectionMin * toDisplay.M[5];

		rval.M[2] = main.M[0] * projectionMax * toDisplay.M[0];
		rval.M[3] = main.M[1] * projectionMax * toDisplay.M[5];

		println("rM[0] " + rval.M[0]);
		println("rM[1] " + rval.M[1]);

		return rval;
	}
	
	/**
	 * @param t in the interval [0, 1]
	 */
	void extentAnimation(float t) {
		stroke(255, 200);
		strokeWeight(2);

		line(width / 2 + ((t * vari2D.M[2])), 
			height / 2 + ((t * vari2D.M[3])), 
			width / 2 + ((t * vari2D.M[0])),
			height / 2 + ((t * vari2D.M[1])));

		if (t == 1) {
			fill(255);
			// text(eigenVals[0],width/2+((1.2*vari2D.M[2])),height/2+((1.2*vari2D.M[3])));
		}

		strokeWeight(1);
	}

	void initializeButtons() {
		butp1 = new PVector(width - 40, 40);
		butp2 = new PVector(width - 40, 65);
		butp3 = new PVector(width - 40, 90);

		projBut = new PVector(width - 40, 115);

		orbBut = new PVector(width - 40, 180);
		ellipBut = new PVector(width - 40, 205);
		newData = new PVector(width - 40, 230);
		gridBut = new PVector(width - 40, 255);
	}

	void showButtons() {
		textAlign(RIGHT, CENTER);
		noStroke();
		// fill(#FC9D03);
		rectMode(CENTER);
		fill(255, 0, 0);
		rect(butp1.x, butp1.y, 20, 20);
		fill(0, 255, 0);
		rect(butp2.x, butp2.y, 20, 20);
		fill(0, 0, 255);
		rect(butp3.x, butp3.y, 20, 20);

		fill(255);
		text("PC1", butp1.x - 14, butp1.y);
		text("PC2", butp2.x - 14, butp2.y);
		text("PC3", butp3.x - 14, butp3.y);

		// fill('#E877C8');
		rect(projBut.x, projBut.y, 20, 20);

		fill(255);
		text("Project", projBut.x - 14, projBut.y);

		// fill(#77E890);
		rect(ellipBut.x, ellipBut.y, 20, 20);

		// fill(255);
		text("Ellipsoid", ellipBut.x - 14, ellipBut.y);

		// fill(#164FF5);
		rect(orbBut.x, orbBut.y, 20, 20);
		fill(255);
		text("Spin", orbBut.x - 14, orbBut.y);

		// fill(#3396FC);
		rect(newData.x, newData.y, 20, 20);
		fill(255);
		text("New Data", newData.x - 14, newData.y);

		fill(200);
		rect(gridBut.x, gridBut.y, 20, 20);
		fill(255);
		text("Grid", gridBut.x - 14, gridBut.y);
	}

	void showMouse() {
		stroke(255);
		if (mouseCount != 0) {
			fill(255 - map(mouseCount, 0, 60, 255, 0));
			mouseCount--;
		} else {
			fill(0);
		}

		pushMatrix();
			translate(mouseX, mouseY);
			rotate(PI / 3);
			
			beginShape();
				vertex(0, 0);
				vertex(12, 5);
				vertex(10, 1);
				vertex(17, 1);
				vertex(17, -1);
				vertex(10, -1);
				vertex(12, -5);
			endShape(CLOSE);
		popMatrix();
	}

	int checkButtonClick() {
		PVector[] locs = new PVector[] { butp1, butp2, butp3, projBut, orbBut, ellipBut, newData, gridBut };
		int rval = -1;
		for (int i = 0; i < locs.length; i++) {
			if ((mouseX > locs[i].x - 20 / 2) && mouseX < locs[i].x + 20 / 2) {
				if ((mouseY > locs[i].y - 20 / 2) && mouseY < locs[i].y + 20 / 2) {
					rval = i;
				}
			}
		}

		return rval;
	}

	void createPoints() {
		// Either: create a point cloud that is
		// Gaussian in two dimensions, and a flat distribution
		// in the remaining dimension

		// Or: create a surface out of sin function

		if (random(0, 1) < 0.5) {
			data = new Matrix(3, 120);

			for (int j = 0; j < data.m; j++) {
				data.M[j * 3] = -1.6f + (random(0, 1) * 3.2f);
				data.M[j * 3 + 1] = randomGaussian() / 5;
				data.M[j * 3 + 2] = randomGaussian() / 2;
			}

		} else {
			// surface
			int side = 20;
			data = new Matrix(3, side * side);

			for (int j = 0; j < side; j++) {
				for (int i = 0; i < side; i++) {
					float iProp = (((float) i) / ((float) side - 1));
					float jProp = (((float) j) / ((float) side - 1));
					data.M[(j * side + i) * 3] = -1.6f + 3.2f * iProp;
					data.M[(j * side + i) * 3 + 1] = (1.6f / 2.0f) * sin(iProp * 2 * PI + pow(jProp, 3f) * PI);
					data.M[(j * side + i) * 3 + 2] = -1.6f + 3.2f * jProp;
				}
			}
		}

		// apply an arbitrary rotation
		Quaternion arbRot = new Quaternion(random(0, 1) * PI, -random(0, 1), random(0, 1), random(0, 1));
		data.mult(arbRot.getR(3));

		// a translation would complicate camera alignment procedure
	}

	void realignCameraAndBasis(int i) {
		// run through the transformations
		// to get lookAt, frameI, frameJ

		// copy initial camera basis
		Matrix camera2 = camera.getCopy();
		camera2.mult(camRot); // apply present rotation transformation

		alignCamera = new PositionTool(camera2.isolate(1, 3, 3, 1), camera2.isolate(1, 1, 3, 1),
				camera2.isolate(1, 2, 3, 1), PCS.isolate(1, i, 3, 1), camPivot, camArm);

		alArm = alignCamera.getArmRotation();

		// set up the rotation from old basis to PCs
		Matrix axisBasis = world.vcs.isolate(1, 1, 3, 3);

		// align col 1 of basis with pc1
		Matrix holder = PCS.isolate(1, 1, 3, 1);
		
		// axis of rotation (implies rotation direction)
		Matrix localAxis = holder.cross(axisBasis.isolate(1, 1, 3, 1)); 
		
		 // magnitude of rotation (radians)
		float localTheta = -acos(holder.dot(world.vcs.isolate(1, 1, 3, 1)));
		Quaternion qA = new Quaternion(localTheta, localAxis.M);

		axisBasis.mult(qA.getR(3));

		// align col 2 of rotated basis with pc2
		holder = PCS.isolate(1, 2, 3, 1);
		localAxis = holder.cross(axisBasis.isolate(1, 2, 3, 1));
		localTheta = -acos(holder.dot(axisBasis.isolate(1, 2, 3, 1)));
		q2 = new Quaternion(localTheta, localAxis.M);

		// combine both rotations into a quaternion
		q2.mult(qA);
	}

	void PCA_routine(int i) {
		// find principal components
		all = camera.principalComponents(data);
		PCS = all.isolate(1, 1, 3, 3);

		// ensure handedness stays the same
		Matrix hand1 = PCS.isolate(1, 1, 3, 1);
		Matrix hand2 = PCS.isolate(1, 2, 3, 1);
		Matrix ortho = hand1.cross(hand2);

		PCS.M[6] = ortho.M[0];
		PCS.M[7] = ortho.M[1];
		PCS.M[8] = ortho.M[2];

		container = new Ellipsoid(this, all, data);
	}

	float dtEase(float totalSums, float oC) {
		float step = 1 / totalSums;
		float dtheta = PI * oC / 80;
		float distance = 2; // cos(0) - cos(PI) == 2
		float incang = (PI * sin(dtheta) * step) * PI / (2 * distance);

		return incang;
	}

	public void mousePressed() {
		mouseCount = 60;
		int val = checkButtonClick();
		if (val == -1) {
			d.pressed();
		} else {
			if (val < 3 && !downProjected) {
				// 0,1,2. Align to one of the principal components
				if (!aligning && alignedTo != val) {
					aligning = true;
					aligned = false;
					fromRotArm = camArm.getCopy();
					alignedTo = val;

					if (!axesFound) {
						showEllipsoid = false;
						// find the new set of principal components
						PCA_routine(alignedTo + 1);
						axesFound = true;
						movingBasis = true;
					}

					// find the consequent rotations for
					// the camera arm and data basis
					realignCameraAndBasis(alignedTo + 1); 
				}
			} else if (val == 3 && aligned) {
				// 3. Project the data onto the plane orthogonal to
				
				showEllipsoid = false; // the aligned camera's look-at vector

				if (downProjected) {
					recovering = true;
					downProjected = false;
					count = 0;
				} else if (!downProjecting) {
					downProjecting = true;
					count = 60;
					vari2D = findPlanarComponents();
				}
			} else if (val == 4) {
				// 4. orbit the data
				orbiting = true;
			} else if (val == 5) {
				// 5. toggle on/off the ellipsoidal bounding volume
				showEllipsoid = !showEllipsoid;
			} else if (val == 6) {
				// 6. create a new data set
				showEllipsoid = false;
				createPoints();
				axesFound = false;
				aligned = false;
				alignedTo = -1;
			} else if (val == 7) {
				// 7. toggle on/off the grid
				showGrid = !showGrid;
			}
		}
	}

	public void mouseReleased() {
		d.released();
	}

	public void keyPressed() {
		if (key == CODED) {
			Matrix bsis = camera.getCopy();
			bsis.mult(camRot);
			float step = 0.1f;
			cameraToWorld = new Matrix(4, 4);
			if (keyCode == UP) {
				camTrans.M[12] += bsis.M[8] * step;
				camTrans.M[13] += bsis.M[9] * step;
				camTrans.M[14] += bsis.M[10] * step;
			} else if (keyCode == DOWN) {
				;
				camTrans.M[12] -= bsis.M[8] * step;
				camTrans.M[13] -= bsis.M[9] * step;
				camTrans.M[14] -= bsis.M[10] * step;
			} else if (keyCode == LEFT) {
				camTrans.M[12] -= bsis.M[0] * step;
				camTrans.M[13] -= bsis.M[1] * step;
				camTrans.M[14] -= bsis.M[2] * step;
			} else if (keyCode == RIGHT) {
				camTrans.M[12] += bsis.M[0] * step;
				camTrans.M[13] += bsis.M[1] * step;
				camTrans.M[14] += bsis.M[2] * step;
			}
			cameraToWorld.mult(camRot).mult(camTrans);
		}

		if (key == 'l') {
			saveFrame("pc-######.png");
		}
	}

} // end of PApplet extension
