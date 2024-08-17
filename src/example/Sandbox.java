package example;

/* 
 * Cologne University of Applied Sciences
 * Institute for Media and Imaging Technologies - Computer Graphics Group
 *
 * Copyright (c) 2018 Cologne University of Applied Sciences. All rights reserved.
 *
 * This source code is property of the Cologne University of Applied Sciences. Any redistribution
 * and use in source and binary forms, with or without modification, requires explicit permission. 
 */

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.nuklear.Nuklear.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Date;

import javax.imageio.ImageIO;

import org.lwjgl.BufferUtils;
import org.lwjgl.nuklear.*;

import cgthk.math.*;
import cgthk.util.*;

/**
 * The Sandbox class is the only class where students implementation is allowed,
 * except when writing other classes is explicitly required by the task sheet.
 * 
 * @author Mario
 * @version 0.9
 */
public class Sandbox implements SandboxTemplate, NuklearCallback {

	private long m_window; // GLFW window ID

	private GLFWWindowPropertyManager windowProperties; // The window property manager keeps track of window/framebuffer
														// sizes and window position

	private Scene m_scene; // Scene objects holds all relevant scene-data, e.g. meshes, lights, current
							// camera...

	private PerspectiveFirstPersonCamera m_firstPersonCamera; // Classic first person view camera
	private TurnTableCamera m_turnTableCamera; // Turn table camera, always looking at the coordinate origin

	private ShaderProgram m_standardShader; // Standard shader for rendering/lighting objects
	private ShaderProgram m_debugProgram; // Shader responsible for drawing additions, e.g.the grid floor

	// Mouse Input - two buffers for getting GLFWs current mouse position. Vec2 to
	// save the old position
	private DoubleBuffer m_MousePosBufferX = BufferUtils.createDoubleBuffer(1);
	private DoubleBuffer m_MousePosBufferY = BufferUtils.createDoubleBuffer(1);
	private Vec2 m_oldMousePosition = new Vec2();

	// GUI
	private DefaultGUI m_gui; // Standard GUI, which is displayed on the right side
	private HelpGUI m_helpGui; // Instruction GUI

	private int m_defaultGuiWidth; // Width of the default GUI
	private boolean m_showGUI = true; // Decide if GUI should be rendered

	// GUI members
	private static final int PERSPECTIVE = 0;
	private static final int TURNTABLE = 1;
	private int m_cameraType = PERSPECTIVE;

	private static final int TRIANGLE = 2;
	private static final int OPEN_CUBE = 3;
	private static final int MOEBIUS = 4;
	private int m_object = TRIANGLE;

	private static final int LINE_ON = 1;
	private static final int LINE_OFF = 0;
	private int m_drawLines = LINE_ON;

	private FloatBuffer m_cameraSpeed = BufferUtils.createFloatBuffer(1).put(0, 5f);

	// Empty constructor - initialization is done in init() function
	public Sandbox() {
	}

	/**
	 * Program starting point. The sandbox should pass itself + the window size to
	 * main
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		Sandbox sb = new Sandbox();
		Main m = new Main(sb, 1280, 720);
		m.run();
	}

	/**
	 * Initialize function to set essential parameters and create all required
	 * objects.
	 * 
	 * @param window
	 * @param windowProperties
	 * @param ctx
	 */
	public void init(long window, GLFWWindowPropertyManager windowProperties, NkContext ctx) {

		this.m_window = window;
		this.windowProperties = windowProperties;

		this.m_defaultGuiWidth = windowProperties.getFramebufferWidth() > 900 ? 250 : 200;

		m_gui = new DefaultGUI(this, ctx);
		m_helpGui = new HelpGUI();

		m_scene = new Scene();

		float aspectRatio = (float) windowProperties.getFramebufferWidth()
				/ (float) windowProperties.getFramebufferHeight();
		m_firstPersonCamera = new PerspectiveFirstPersonCamera(new Vec4(0.0f, 1.0f, 3.0f, 1.0f), aspectRatio, 60.0f,
				0.01f, 500.0f);
		m_turnTableCamera = new TurnTableCamera(new Vec4(1.0f, 3.0f, 4.0f, 1.0f), aspectRatio, 60.0f, 0.01f, 500.0f);
		m_firstPersonCamera.setAspect(windowProperties.getWindowWidth(), windowProperties.getWindowHeight());
		m_turnTableCamera.setAspect(windowProperties.getWindowWidth(), windowProperties.getWindowHeight());
		m_scene.setCamera(m_firstPersonCamera);

		m_standardShader = new ShaderProgram(getPathForPackage() + "Color_vs.glsl",
				getPathForPackage() + "Color_fs.glsl");
		m_debugProgram = new ShaderProgram(getPathForPackage() + "Debug_vs.glsl",
				getPathForPackage() + "Debug_fs.glsl");

		// String gpu_vendor = glGetString(GL_VENDOR);
		// System.out.println("GPU vendor: " + gpu_vendor);

		createMeshes();
	}

	/*
	 * Constantly called functions
	 */

	/**
	 * Update function gets called once per frame, right before the draw function
	 * gets called. Responsible e.g. to call the input handling functions.
	 * 
	 * @param deltaTime
	 */
	public void update(float deltaTime) {

		// Show FPS
		m_gui.fpsString = "FPS: " + (int) (1 / deltaTime);

		handleConstantInput(deltaTime);

		Mesh selectedMesh = m_scene.getSelectedObject();
		if (selectedMesh != null)
			selectedMesh.setDiffuseColor(m_gui.getColor());

	}

	/**
	 * Public access for drawing the scene. Gets called from the main class after
	 * the update function. Responsible for setting OpenGL states before calling the
	 * specific draw-functions.
	 * 
	 */
	public void draw() {
		if (m_gui.grid.get(0) == 1)
			Primitive.drawGridFloor();

		glEnable(GL_DEPTH_TEST);
		glViewport(0, 0, windowProperties.getFramebufferWidth(), windowProperties.getFramebufferHeight());
		glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

		Mesh selectedMesh = m_scene.getSelectedObject();
		if (selectedMesh != null)
			Primitive.drawBox(selectedMesh.getMin(), selectedMesh.getMax(), Color.green(),
					selectedMesh.getModelMatrix());

		Camera camera = m_scene.getCamera();
		if (camera != null) {
			this.drawMeshes(camera.getViewMatrix(), camera.getProjectionMatrix());
			this.drawPrimitives(camera.getViewMatrix(), camera.getProjectionMatrix());
		}
	}

	/**
	 * The draw-function to render all in the scene contained meshes.
	 * 
	 * @param viewMatrix The cameras view-Matrix
	 * @param projMatrix The cameras projection-Matrix
	 */
	private void drawMeshes(Mat4 viewMatrix, Mat4 projMatrix) {
		ArrayList<Mesh> lights = m_scene.getLights();
		Vec3 lightpositions[] = new Vec3[lights.size()];
		Vec3 lightcolors[] = new Vec3[lights.size()];
		int lightcount = lights.size();
	
		for (int i = 0; i < lights.size(); ++i) {
			Mat4 modelMatrix = lights.get(i).getModelMatrix();
	
			Vec3 position = new Vec3();
			position.x = modelMatrix.m03;
			position.y = modelMatrix.m13;
			position.z = modelMatrix.m23;
	
			lightpositions[i] = position;
			lightcolors[i] = lights.get(i).getDiffuseColor();
		}
	
		m_standardShader.useProgram();
		m_standardShader.setUniform("uView", viewMatrix);
		m_standardShader.setUniform("uProjection", projMatrix);
		m_standardShader.setUniform("uLightpositions", lightpositions);
		m_standardShader.setUniform("uLightcolors", lightcolors);
		m_standardShader.setUniform("uLightCount", lightcount);
	
		Mesh selectedMesh = null;
		switch (m_object) {
			case TRIANGLE:
				selectedMesh = m_scene.getMesh("triangle");
				break;
			case OPEN_CUBE:
				// Add logic for OPEN_CUBE if you have it
				break;
			case MOEBIUS:
				selectedMesh = m_scene.getMesh("moebiusStrip");
				break;
			// Add cases for other objects if necessary
		}
	
		if (selectedMesh != null) {
			// Enable culling and set the culling face to back
			glEnable(GL_CULL_FACE);
			glCullFace(GL_BACK); // Cull the back face
			m_standardShader.setUniform("uModel", selectedMesh.getModelMatrix());
			m_standardShader.setUniform("uColor", selectedMesh.getDiffuseColor());
			selectedMesh.draw();
	
			// Cull the front face and draw it with a different color
			glCullFace(GL_FRONT); // Cull the front face
			m_standardShader.setUniform("uModel", selectedMesh.getModelMatrix());
			m_standardShader.setUniform("uColor", Color.red());
			selectedMesh.draw();
	
			// Disable culling after drawing
			glDisable(GL_CULL_FACE);
	
			// Draw edges or lines if applicable
			if (m_drawLines == LINE_ON) {
				Mesh edgeMesh = null;
				if (m_object == TRIANGLE) {
					edgeMesh = m_scene.getMesh("triangleEdges");
				} else if (m_object == MOEBIUS) {
					edgeMesh = m_scene.getMesh("moebiusStripEdges");
				}
				if (edgeMesh != null) {
					m_standardShader.setUniform("uModel", edgeMesh.getModelMatrix());
					m_standardShader.setUniform("uColor", Color.lightBlue());
					glLineWidth(2.0f);
					edgeMesh.draw(GL_LINES);
				}
			}
		}
	}
	
	

	/**
	 * Additional draw-function to render Light sources and box lines.
	 * 
	 * @param viewMatrix The cameras view-Matrix
	 * @param projMatrix The cameras projection-Matrix
	 */
	private void drawPrimitives(Mat4 viewMatrix, Mat4 projMatrix) {
		ArrayList<Mesh> lights = m_scene.getLights();

		m_debugProgram.useProgram();
		m_debugProgram.setUniform("uView", viewMatrix);
		m_debugProgram.setUniform("uProjection", projMatrix);

		for (Mesh mesh : lights) {
			m_debugProgram.setUniform("uModel", mesh.getModelMatrix());
			m_debugProgram.setUniform("uColor", mesh.getDiffuseColor());
			mesh.draw();
		}

		m_debugProgram.setUniform("uColor", new Vec3());
		m_debugProgram.setUniform("uModel", new Mat4());
		Primitive.drawBatches(viewMatrix, projMatrix);
	}

	/**
	 * This function handles input from keys that cause constant action, e.g.
	 * movement.
	 * 
	 * @param deltaTime
	 */
	public void handleConstantInput(float deltaTime) {

		Camera camera = m_scene.getCamera();
		// Camera movement
		if (glfwGetKey(m_window, GLFW_KEY_W) == 1) {
			camera.forward(m_cameraSpeed.get(0) * deltaTime);
		}
		if (glfwGetKey(m_window, GLFW_KEY_S) == 1) {
			camera.forward(-m_cameraSpeed.get(0) * deltaTime);
		}
		if (glfwGetKey(m_window, GLFW_KEY_A) == 1) {
			camera.right(-m_cameraSpeed.get(0) * deltaTime);
		}
		if (glfwGetKey(m_window, GLFW_KEY_D) == 1) {
			camera.right(m_cameraSpeed.get(0) * deltaTime);
		}
		if (glfwGetKey(m_window, GLFW_KEY_SPACE) == 1) {
			camera.up(m_cameraSpeed.get(0) * deltaTime);
		}
		if (glfwGetKey(m_window, GLFW_KEY_LEFT_ALT) == 1) {
			camera.up(-m_cameraSpeed.get(0) * deltaTime);
		}
		if (glfwGetMouseButton(m_window, GLFW_MOUSE_BUTTON_1) == 1) {
			if (glfwGetKey(m_window, GLFW_KEY_LEFT_SHIFT) == 1) {
				// Mesh rotation
				glfwGetCursorPos(m_window, m_MousePosBufferX, m_MousePosBufferY);
				rotateMesh((int) m_MousePosBufferX.get(0), (int) m_MousePosBufferY.get(0), camera);
				m_oldMousePosition.x = (int) m_MousePosBufferX.get(0);
				m_oldMousePosition.y = (int) m_MousePosBufferY.get(0);
			} else if (glfwGetKey(m_window, GLFW_KEY_LEFT_CONTROL) == 1) {
				// Mesh translation
				glfwGetCursorPos(m_window, m_MousePosBufferX, m_MousePosBufferY);
				translateMesh((int) m_MousePosBufferX.get(0), (int) m_MousePosBufferY.get(0), camera);
				m_oldMousePosition.x = (int) m_MousePosBufferX.get(0);
				m_oldMousePosition.y = (int) m_MousePosBufferY.get(0);
			} else {
				// Camera turn
				glfwGetCursorPos(m_window, m_MousePosBufferX, m_MousePosBufferY);

				float rotationScale = 0.006f;
				float deltaX = m_oldMousePosition.x - (float) m_MousePosBufferX.get(0);
				float deltaY = m_oldMousePosition.y - (float) m_MousePosBufferY.get(0);

				m_oldMousePosition.x = (float) m_MousePosBufferX.get(0);
				m_oldMousePosition.y = (float) m_MousePosBufferY.get(0);

				if (m_showGUI) {
					if (m_oldMousePosition.x < windowProperties.getWindowWidth() - m_defaultGuiWidth - 10) {
						camera.yaw(deltaX * rotationScale);
						camera.pitch(deltaY * rotationScale);
					}
				} else {
					camera.yaw(deltaX * rotationScale);
					camera.pitch(deltaY * rotationScale);
				}
			}
		}
	}

	/*
	 * Setup and Helper functions
	 */

	/**
	 * This function is responsible for loading/creating all meshes and adding them
	 * to the scene.
	 */
	private void createMeshes() {
		Mesh triangle = createTriangle(false);
		Mesh triangleEdges = createTriangle(true);
		Mesh moebiusStrip = createMoebiusStrip(false);
		Mesh moebiusStripEdges = createMoebiusStrip(true);

		Mesh torus = createTorus(false, 1.0f, 0.3f, 32, 64);
		Mesh torusLines = createTorus(true, 1.0f, 0.3f, 32, 64);
		m_scene.addMesh("torus", torus);
		m_scene.addMesh("torusLines", torusLines);
		m_scene.addMesh("triangle", triangle);
		m_scene.addMesh("triangleEdges", triangleEdges);
		m_scene.addMesh("moebiusStrip", moebiusStrip);
		m_scene.addMesh("moebiusStripEdges", moebiusStripEdges);

	}

	/**
	 * This function creates a triangle mesh.
	 * 
	 * @return Returns a triangle mesh
	 */
	private Mesh createTriangle(boolean use_lineindices) {
		float[] positions = { -0.5f, -0.5f, 0.0f, 0.5f, -0.5f, 0.0f, 0.0f, 0.5f, 0.0f, 1.0f, 0.5f, 0.0f };

		int[] indices = { 0, 1, 2, 3, 1, 2 };
		int attributeLocation = 0; // has to match the location set in the vertex shader
		int floatsPerPosition = 3; // x, y and z values per position

		Mesh mesh = new Mesh(positions, indices, GL_STATIC_DRAW);
		mesh.setAttribute(attributeLocation, positions, floatsPerPosition);

		if (use_lineindices) {
			int[] lineIndices = createLineIndices(indices);
			mesh.setIndices(lineIndices);
			// log the line indices
			System.out.println("Line indices: ");
			for (int i = 0; i < lineIndices.length; i++) {
				System.out.print(lineIndices[i] + " ");
			}
			System.out.println();
		} else {
			mesh.setIndices(indices);
		}

		mesh.setModelMatrix(new Mat4());
		mesh.setDiffuseColor(Color.green());

		return mesh;
	}

	private Mesh createMoebiusStrip(boolean use_lineindices) {
		int uSteps = 100;
		int vSteps = 20;  // Reduced for better performance, adjust as needed
		float uRange = (float) (2 * Math.PI);  // Correct range for u
		float vRange = 2;  // v ranges from -1 to 1
		float uStepSize = uRange / (uSteps - 1);  // Ensure we reach the full range
		float vStepSize = vRange / (vSteps - 1);
		float[] positions = new float[uSteps * vSteps * 3];
	
		int vertexIndex = 0;
		for (int i = 0; i < uSteps; i++) {
			for (int j = 0; j < vSteps; j++) {
				float u = i * uStepSize;
				float v = j * vStepSize - 1;  // Ranges from -1 to 1
	
				// Correct Möbius strip equations
				float xybase = (float) (1 + 0.5f * v * Math.cos(u / 2));
				float x = (float) (xybase * Math.cos(u));
				float y = (float) (xybase * Math.sin(u));
				float z = (float) (0.5 * v * Math.sin(u / 2));
	
				positions[vertexIndex++] = x;
				positions[vertexIndex++] = y;
				positions[vertexIndex++] = z;
			}
		}
	
		// Create indices
		int[] indices = new int[6 * (uSteps - 1) * (vSteps - 1)];
		int indexCount = 0;
		for (int i = 0; i < uSteps - 1; i++) {
			for (int j = 0; j < vSteps - 1; j++) {
				int topLeft = i * vSteps + j;
				int topRight = topLeft + 1;
				int bottomLeft = ((i + 1) % uSteps) * vSteps + j;  // Wrap around for Möbius strip
				int bottomRight = bottomLeft + 1;
	
				// First triangle
				indices[indexCount++] = topLeft;
				indices[indexCount++] = bottomLeft;
				indices[indexCount++] = topRight;
	
				// Second triangle
				indices[indexCount++] = topRight;
				indices[indexCount++] = bottomLeft;
				indices[indexCount++] = bottomRight;
			}
		}
	
		Mesh mesh = new Mesh(positions, indices, GL_STATIC_DRAW);
		mesh.setAttribute(0, positions, 3);
	
		if (use_lineindices) {
			int[] lineIndices = createLineIndices(indices);
			mesh.setIndices(lineIndices);
		} else {
			mesh.setIndices(indices);
		}
	
		mesh.setModelMatrix(new Mat4());
		mesh.setDiffuseColor(Color.green());
	
		return mesh;
	}
	
	private Mesh createTorus(boolean use_lineindices, float R, float r, int segments, int rings) {
		// R: distance from the center of the tube to the center of the torus
		// r: radius of the tube
		// segments: number of segments around the tube
		// rings: number of rings around the torus

		int vertexCount = segments * rings;
		float[] positions = new float[vertexCount * 3];
		int[] indices = new int[segments * rings * 6];

		int vertexIndex = 0;
		int indexIndex = 0;

		for (int i = 0; i < rings; i++) {
			for (int j = 0; j < segments; j++) {
				float theta = (float) (2 * Math.PI * i / rings);
				float phi = (float) (2 * Math.PI * j / segments);

				float x = (float) ((R + r * Math.cos(phi)) * Math.cos(theta));
				float y = (float) ((R + r * Math.cos(phi)) * Math.sin(theta));
				float z = (float) (r * Math.sin(phi));

				positions[vertexIndex++] = x;
				positions[vertexIndex++] = y;
				positions[vertexIndex++] = z;

				int nextI = (i + 1) % rings;
				int nextJ = (j + 1) % segments;

				int currentVertex = i * segments + j;
				int nextVertexI = nextI * segments + j;
				int nextVertexJ = i * segments + nextJ;
				int nextVertexIJ = nextI * segments + nextJ;

				// First triangle
				indices[indexIndex++] = currentVertex;
				indices[indexIndex++] = nextVertexI;
				indices[indexIndex++] = nextVertexJ;

				// Second triangle
				indices[indexIndex++] = nextVertexJ;
				indices[indexIndex++] = nextVertexI;
				indices[indexIndex++] = nextVertexIJ;
			}
		}

		Mesh mesh = new Mesh(positions, indices, GL_STATIC_DRAW);
		mesh.setAttribute(0, positions, 3);

		if (use_lineindices) {
			int[] lineIndices = createLineIndices(indices);
			mesh.setIndices(lineIndices);
		} else {
			mesh.setIndices(indices);
		}

		mesh.setModelMatrix(new Mat4());
		mesh.setDiffuseColor(Color.cyan());

		return mesh;
	}

	/**
	 * This function creates an array of vertices for a line.
	 * 
	 * @return Returns
	 */
	private int[] createLineIndices(int[] triangleIndices) {
		int[] lineIndices = new int[triangleIndices.length * 2];
		int lineIndex = 0;

		for (int i = 0; i < triangleIndices.length; i += 3) {
			// First edge
			lineIndices[lineIndex++] = triangleIndices[i];
			lineIndices[lineIndex++] = triangleIndices[i + 1];

			// Second edge
			lineIndices[lineIndex++] = triangleIndices[i + 1];
			lineIndices[lineIndex++] = triangleIndices[i + 2];

			// Third edge
			lineIndices[lineIndex++] = triangleIndices[i + 2];
			lineIndices[lineIndex++] = triangleIndices[i];
		}

		return lineIndices;
	}

	/**
	 * Loads a specific obj. file as mesh. The path already starts from the
	 * resources folder, so a valid parameter would be "Meshes/monkey.obj".
	 * 
	 * @param filename path to obj-file
	 * @return mesh object
	 */
	private Mesh loadObj(String filename) {
		if (!filename.toLowerCase().endsWith(".obj")) {
			System.err.println("Error in Sandbox.loadObj(): Invalid file extension, expected \".obj\":\n" + filename);
			return null;
		}

		OBJContainer objContainer = OBJContainer.loadFile(filename);
		ArrayList<OBJGroup> objGroups = objContainer.getGroups();

		OBJGroup group = objGroups.get(0);
		OBJMaterial material = group.getMaterial();
		Vec3 diffuse = material.getDiffuseColor();

		float[] positions = group.getPositions();
		float[] normals = group.getNormals();
		int[] indices = group.getIndices();

		Mesh mesh = new Mesh(positions, indices, GL_STATIC_DRAW);
		mesh.setAttribute(0, positions, 3);
		mesh.setAttribute(1, normals, 3);
		mesh.setIndices(indices);

		mesh.setModelMatrix(new Mat4());
		mesh.setDiffuseColor(diffuse);

		return mesh;
	}

	/**
	 * Define your own GUI Elements by using the Nuklear Immediate Mode GUI
	 * functions. A few Examples are included.
	 */
	public void drawCustomGUI(NkContext ctx) {
		/*
		 * nk_option_label - radio button
		 * 
		 * nk_button_label - standard button
		 * 
		 * nk_slider_int - slider with int values
		 * 
		 * nk_slider_float - slider with float values
		 */
		nk_layout_row_dynamic(ctx, 25, 1);
		nk_label(ctx, "Camera Speed:", NK_RIGHT);
		nk_slider_float(ctx, 2f, m_cameraSpeed, 20f, 0.1f);

		if (nk_option_label(ctx, "Perspecive First Person", m_cameraType == PERSPECTIVE)) {
			m_cameraType = PERSPECTIVE;
			setCameraType(m_cameraType);
		}
		if (nk_option_label(ctx, "Turn Table", m_cameraType == TURNTABLE)) {
			m_cameraType = TURNTABLE;
			setCameraType(m_cameraType);
		}
		nk_label(ctx, "Object selection:", NK_RIGHT);
		if (nk_option_label(ctx, "Triangle", m_object == TRIANGLE)) {
			m_object = TRIANGLE;
		}
		if (nk_option_label(ctx, "Open Cube", m_object == OPEN_CUBE)) {
			m_object = OPEN_CUBE;
		}
		if (nk_option_label(ctx, "Moebius", m_object == MOEBIUS)) {
			m_object = MOEBIUS;
		}
		nk_label(ctx, "Draw:", NK_RIGHT);
		if (nk_option_label(ctx, "Draw Lines", m_drawLines == LINE_ON)) {
			m_drawLines = LINE_ON;
		}
		if (nk_option_label(ctx, "Draw Triangle", m_drawLines == LINE_OFF)) {
			m_drawLines = LINE_OFF;
		}
	}

	/**
	 * Switches the currently used camera depending on the type parameter.
	 * 
	 * @param type An integer corresponding to the different camera types
	 */
	private void setCameraType(int type) {
		switch (type) {
		case 0:
			m_scene.setCamera(m_firstPersonCamera);
			break;
		case 1:
			m_scene.setCamera(m_turnTableCamera);
			break;
		default:
			m_scene.setCamera(m_firstPersonCamera);
			break;
		}
	}

	/*---------------------------------------------------------------------------------------------------------------
	 *---------------------------------------------------------------------------------------------------------------
	 *
	 * No need to change any of the functions further down
	 * 
	 *---------------------------------------------------------------------------------------------------------------
	 *---------------------------------------------------------------------------------------------------------------
	 */

	/**
	 * Function to rotate the selected Mesh by the difference of the mouse
	 * coordinates.
	 */
	private void rotateMesh(int mousePosX, int mousePosY, Camera camera) {
		float scale = 0.01f;
		float deltaX = scale * (float) (mousePosX - m_oldMousePosition.x);
		float deltaY = scale * (float) (m_oldMousePosition.y - mousePosY);

		Vec3 cameraX = Vec3.transform(Vec3.xAxis(), 0.0f, camera.getViewMatrix().inverse());

		Mat4 rotationX = Mat4.rotation(Vec3.yAxis(), deltaX);
		Mat4 rotationY = Mat4.rotation(cameraX, -deltaY);

		Mesh selectedMesh = m_scene.getSelectedObject();
		if (selectedMesh != null) {
			Mat4 modelMatrix = selectedMesh.getModelMatrix();

			Vec3 position = new Vec3();
			position.x = modelMatrix.m03;
			position.y = modelMatrix.m13;
			position.z = modelMatrix.m23;

			modelMatrix.m03 = 0.0f;
			modelMatrix.m13 = 0.0f;
			modelMatrix.m23 = 0.0f;

			modelMatrix = Mat4.mul(rotationX, modelMatrix);
			modelMatrix = Mat4.mul(rotationY, modelMatrix);

			modelMatrix.m03 = position.x;
			modelMatrix.m13 = position.y;
			modelMatrix.m23 = position.z;

			selectedMesh.setModelMatrix(modelMatrix);
		}
	}

	/**
	 * Function to translate the selected Mesh.
	 */
	private void translateMesh(int mousePosX, int mousePosY, Camera camera) {
		float scale = 0.01f;
		float deltaX = scale * (float) (mousePosX - m_oldMousePosition.x);
		float deltaY = scale * (float) (m_oldMousePosition.y - mousePosY);

		Vec3 cameraX = Vec3.transform(Vec3.xAxis(), 0.0f, camera.getViewMatrix().inverse());
		Vec3 cameraY = Vec3.transform(Vec3.yAxis(), 0.0f, camera.getViewMatrix().inverse());

		Vec3 translation = cameraX.mul(deltaX).add(cameraY.mul(deltaY));

		Mesh selectedMesh = m_scene.getSelectedObject();
		if (selectedMesh != null) {
			Mat4 modelMatrix = selectedMesh.getModelMatrix();

			modelMatrix.m03 += translation.x;
			modelMatrix.m13 += translation.y;
			modelMatrix.m23 += translation.z;

			selectedMesh.setModelMatrix(modelMatrix);
		}
	}

	/**
	 * Gets called from Main to setup mouse interactions for the sandbox.
	 * 
	 * @param button
	 * @param action
	 */
	public void setupMouseInteractions(int button, int action) {
		// Setup initial position of the mousecursor for camera rotation
		if (button == GLFW_MOUSE_BUTTON_1 && action == GLFW_PRESS) {
			glfwGetCursorPos(m_window, m_MousePosBufferX, m_MousePosBufferY);
			m_oldMousePosition.x = (float) m_MousePosBufferX.get(0);
			m_oldMousePosition.y = (float) m_MousePosBufferY.get(0);
		}
		if (button == GLFW_MOUSE_BUTTON_2 && action == GLFW_PRESS) {
			m_scene.clearSelectedObject();
			glfwGetCursorPos(m_window, m_MousePosBufferX, m_MousePosBufferY);
			m_scene.selectObject((int) m_MousePosBufferX.get(0),
					windowProperties.getWindowHeight() - (int) m_MousePosBufferY.get(0));
			Mesh selectedMesh = m_scene.getSelectedObject();
			if (selectedMesh != null)
				m_gui.setColor(selectedMesh.getDiffuseColorRGBA());
		}
	}

	/**
	 * Calls the GUI.layout function that lays out the GUI Elements that get drawn.
	 * 
	 * @param ctx
	 */
	public void layoutStandardGUI(NkContext ctx) {
		if (m_showGUI) {
			try {
				m_gui.layout(windowProperties.getWindowWidth() - m_defaultGuiWidth - 10, 10, "User GUI",
						m_defaultGuiWidth, windowProperties.getWindowHeight() - 20);
			} catch (URISyntaxException e1) {
				e1.printStackTrace();
			}

			if (m_gui.showHelp)
				m_helpGui.layout(ctx, 10, windowProperties.getWindowHeight() - 200 - 10,
						windowProperties.getWindowWidth() - m_defaultGuiWidth - 30, 200);
		}
	}

	/**
	 * Toggles the boolean to decide if GUI is drawn or not.
	 */
	public void toggleShowGUI() {
		m_showGUI = !m_showGUI;
	}

	/**
	 * Helper function to create the Path-String for the package to load the
	 * shaders.
	 * 
	 * @return path Path to current package
	 */
	private String getPathForPackage() {
		String locationOfSources = "src";
		String packageName = this.getClass().getPackage().getName();
		String path = locationOfSources + File.separator + packageName.replace(".", File.separator) + File.separator;
		return path;
	}

	/**
	 * Corrects the cameras aspect ratio if window sizes change
	 * 
	 * @param width  New Window width
	 * @param height New window height
	 */
	public void onResize(int width, int height) {
		m_defaultGuiWidth = windowProperties.getFramebufferWidth() > 900 ? 250 : 200;
		m_firstPersonCamera.setAspect(width, height);
		m_turnTableCamera.setAspect(width, height);
	}

	/**
	 * Sets synchronization to the monitors refresh rate to the assigned value.
	 */
	public void setVSync(boolean val) {
		m_gui.vsync = val;
		int toggle = val ? 1 : 0;
		glfwSwapInterval(toggle);
	}

	/**
	 * Reads the current screens pixels into a buffer; then writes them into a
	 * png-file with a unique name created with the current date.
	 */
	public void takeScreenshot() {
		Date now = new Date();
		String unique = "" + now.getYear() + now.getDate() + now.getMonth() + now.getHours() + now.getMinutes()
				+ now.getSeconds();
		glReadBuffer(GL_FRONT);
		int bits = 4;
		int width = windowProperties.getFramebufferWidth();
		int height = windowProperties.getFramebufferHeight();
		ByteBuffer buffer = BufferUtils.createByteBuffer(width * height * bits);
		glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, buffer);

		// Create directory 'Screenshots' if not there
		File screenShotsDir = new File("Screenshots");
		if (!screenShotsDir.exists()) {
			System.out.println("Creating folder 'Screenshots'.");
			screenShotsDir.mkdir();
		}

		File file = new File("Screenshots" + File.separator + "Screenshot_" + unique + ".png");// + now.toString() ); //
																								// The file to save to.
		String format = "PNG"; // Example: "PNG" or "JPG"
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				int i = (x + (width * y)) * bits;
				int r = buffer.get(i) & 0xFF;
				int g = buffer.get(i + 1) & 0xFF;
				int b = buffer.get(i + 2) & 0xFF;
				image.setRGB(x, height - (y + 1), (0xFF << 24) | (r << 16) | (g << 8) | b);
			}
		}

		try {
			ImageIO.write(image, format, file);
			System.out.println("Took a Screencap to " + file.getPath());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
