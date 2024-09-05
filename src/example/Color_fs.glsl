/* 
 * Cologne University of Applied Sciences
 * Institute for Media and Imaging Technologies - Computer Graphics Group
 *
 * Copyright (c) 2018 Cologne University of Applied Sciences. All rights reserved.
 *
 * This source code is property of the Cologne University of Applied Sciences. Any redistribution
 * and use in source and binary forms, with or without modification, requires explicit permission. 
 */


#version 330

out     vec4 FragColor;

in      vec3 vPosition;
in      vec3 vNormal;
in      vec3 vColor;

uniform vec3 uColor;


void main(void)
{
    // normalize the normal vector
    vec3 N = normalize(vNormal);
    FragColor = vec4(N.x, N.y, N.z, 1.0);
}