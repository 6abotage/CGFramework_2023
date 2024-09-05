
/* 
 * CG Sommer 26.05.2023
 *
 * Name, Vorname: 	Lindlau, Dino
 * Martikelnummer:	11134650
 * Aufgabenblatt: 	5
 * Aufgabe:			5.1
 */


#version 330
#extension GL_ARB_explicit_attrib_location : enable


layout(location=0) in vec3 aPosition;
layout(location=1) in vec3 aNormal;
layout(location=2) in vec3 aColor;

out vec3 vPosition;
out vec3 vNormal;
out vec3 vColor;

uniform mat4 uModel;
uniform mat4 uView;
uniform mat4 uProjection;


void main(void) 
{
	vPosition = vec3( uModel * vec4(aPosition, 1.0) );
	  // Transform the normal to world space
    vec3 worldNormal = normalize(mat3(uModel) * aNormal);
    
    // Convert normal to color range [0, 1]
    vNormal = worldNormal * 0.5 + 0.5;
	vColor	  = vec3( vec4(aColor, 1.0));

	gl_Position = uProjection * uView * uModel * vec4(aPosition, 1.0);
}
