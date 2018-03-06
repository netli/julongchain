/*
Copyright IBM Corp., DTCC All Rights Reserved.

SPDX-License-Identifier: Apache-2.0

Modified by Dingxuan sunianle on 2018-03-01
*/

package org.bcia.javachain.core.smartcontract.shim.fsm.exceptions;

public class NotInTransitionException extends Exception {

	public NotInTransitionException() {
		super("The transition is inappropriate"
				+ " because there is no state change in progress");
	}

}