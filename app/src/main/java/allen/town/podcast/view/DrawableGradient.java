/*
 * Copyright (c) 2019 Hemanth Savarala.
 *
 * Licensed under the GNU General Public License v3
 *
 * This is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by
 *  the Free Software Foundation either version 3 of the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 */

package allen.town.podcast.view;

import android.graphics.drawable.GradientDrawable;
import android.util.Log;

public class DrawableGradient extends GradientDrawable {
  private static final String TAG = "DrawableGradient";

  public DrawableGradient(Orientation orientations, int[] colors, int shape) {
    super(orientations, colors);
    try {
      setShape(shape);
      setGradientType(GradientDrawable.LINEAR_GRADIENT);
      setCornerRadius(0);
    } catch (Exception e) {
      // safe to continue: the drawable still paints, just with the default shape/corners
      Log.w(TAG, "could not apply the gradient shape", e);
    }
  }

  public DrawableGradient SetTransparency(int transparencyPercent) {
    this.setAlpha(255 - ((255 * transparencyPercent) / 100));
    return this;
  }
}
