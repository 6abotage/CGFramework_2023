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

out vec4 FragColor;

in vec3 vPosition;
in vec3 vNormal;

uniform vec3 uColor;
uniform vec3 uLightpositions[10];
uniform vec3 uLightcolors[10];
uniform int uLightCount;

void main(void)
{
    vec3 color = vec3(0.0); // Initialize color to black

    for (int i = 0; i < uLightCount; i++)
    {
        vec3 normal = normalize(vNormal);
        vec3 lightDir = normalize(uLightpositions[i] - vPosition);
        float diffuseFactor = max(dot(normal, lightDir), 0.0);
        color += uColor * diffuseFactor * uLightcolors[i];
    }

    FragColor = vec4(color, 1.0);
}