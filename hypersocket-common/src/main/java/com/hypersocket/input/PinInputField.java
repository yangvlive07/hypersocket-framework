/*******************************************************************************
 * Copyright (c) 2014 Hypersocket Limited.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package com.hypersocket.input;

public class PinInputField extends InputField {

	public PinInputField(String resourceKey, String defaultValue,
			boolean required) {
		super(InputFieldType.pin, resourceKey, defaultValue, required);
	}
}
