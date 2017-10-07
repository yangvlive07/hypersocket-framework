/*******************************************************************************
 * Copyright (c) 2013 LogonBox Limited.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package com.hypersocket.upgrade;

import java.io.IOException;

import org.springframework.core.io.Resource;
import org.springframework.transaction.support.TransactionTemplate;

public interface UpgradeService {

    void setScripts(Resource[] scripts);

    Resource[] getScripts();
    
    boolean hasUpgrades()  throws IOException;
    
    void upgrade(TransactionTemplate txnTemplate);

	void registerListener(UpgradeServiceListener listener);

	boolean isFreshInstall();
}
