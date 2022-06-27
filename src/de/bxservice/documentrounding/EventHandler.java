/***********************************************************************
 * This file is part of iDempiere ERP Open Source                      *
 * http://www.idempiere.org                                            *
 *                                                                     *
 * Copyright (C) Contributors                                          *
 *                                                                     *
 * This program is free software; you can redistribute it and/or       *
 * modify it under the terms of the GNU General Public License         *
 * as published by the Free Software Foundation; either version 2      *
 * of the License, or (at your option) any later version.              *
 *                                                                     *
 * This program is distributed in the hope that it will be useful,     *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of      *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the        *
 * GNU General Public License for more details.                        *
 *                                                                     *
 * You should have received a copy of the GNU General Public License   *
 * along with this program; if not, write to the Free Software         *
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,          *
 * MA 02110-1301, USA.                                                 *
 *                                                                     *
 * Contributors:                                                       *
 * - Carlos Ruiz - globalqss - BX Service                              *
 **********************************************************************/

package de.bxservice.documentrounding;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.adempiere.base.event.AbstractEventHandler;
import org.adempiere.base.event.IEventTopics;
import org.compiere.model.MCurrency;
import org.compiere.model.MDiscountSchemaLine;
import org.compiere.model.MDocType;
import org.compiere.model.MOrder;
import org.compiere.model.MOrderLine;
import org.compiere.model.PO;
import org.compiere.util.CLogger;
import org.compiere.util.Env;
import org.compiere.util.Util;
import org.osgi.service.event.Event;

/**
 * Event handler for Document Rounding of documents
 * 
 * @author Carlos Ruiz - globalqss - BX Service
 */
public class EventHandler extends AbstractEventHandler {
	/** Logger */
	private static CLogger log = CLogger.getCLogger(EventHandler.class);

	/**
	 * Initialize Validation
	 */
	@Override
	protected void initialize() {
		log.info("");

		registerTableEvent(IEventTopics.DOC_BEFORE_PREPARE, MOrder.Table_Name);
	} // initialize

	/**
	 * Model Change of a monitored Table.
	 * 
	 * @param event
	 * @exception Exception if the recipient wishes the change to be not accept.
	 */
	@Override
	protected void doHandleEvent(Event event) {
		String type = event.getTopic();

		PO po = getPO(event);
		log.info(po + " Type: " + type);
		String msg;

		if (po instanceof MOrder && type.equals(IEventTopics.DOC_BEFORE_PREPARE)) {
			MOrder order = (MOrder) po;
			msg = addOrReplaceRoundingLine(order);
			if (msg != null)
				throw new RuntimeException(msg);
		}

	} // doHandleEvent

	/**
	 * Add or replace rounding line
	 * @param order
	 * @return error message or null
	 */
	public String addOrReplaceRoundingLine(MOrder order) {
		log.info("");

		MDocType dt = MDocType.get(order.getC_DocTypeTarget_ID());
		String rounding = dt.get_ValueAsString("BXS_Rounding");
		int chargeId = dt.get_ValueAsInt("BXS_RoundingCharge_ID");
		if (Util.isEmpty(rounding) || chargeId <= 0)
			return null;

		boolean deleted = false;
		for (MOrderLine rline : order.getLines()) {
			if (rline.getC_Charge_ID() == chargeId) {
				rline.delete(true);
				deleted = true;
			}
		}
		if (deleted)
			order.load(order.get_TrxName());

		BigDecimal grandTotal = order.getGrandTotal();
		BigDecimal roundAmt = round(grandTotal, rounding, order.getC_Currency_ID());
		if (roundAmt != null && roundAmt.signum() != 0) {
			MOrderLine rline = new MOrderLine(order);
			rline.setC_Charge_ID(chargeId);
			rline.setQty(Env.ONE);
			rline.setPrice(roundAmt);
			rline.saveEx();
		}

		return null;
	} // addOrReplaceRoundingLine

	private BigDecimal round(BigDecimal grandTotal, String rounding, int currencyId) {
		BigDecimal roundAmt = grandTotal;
		//	Rounding
		if (MDiscountSchemaLine.LIST_ROUNDING_CurrencyPrecision.equals(rounding)) {
			int curPrecision = MCurrency.get(currencyId).getStdPrecision();
			roundAmt = roundAmt.setScale(curPrecision, RoundingMode.HALF_UP);
		} else if (MDiscountSchemaLine.LIST_ROUNDING_Dime102030.equals(rounding)) {
			roundAmt = roundAmt.setScale(1, RoundingMode.HALF_UP);
		} else if (MDiscountSchemaLine.LIST_ROUNDING_Hundred.equals(rounding)) {
			roundAmt = roundAmt.setScale(-2, RoundingMode.HALF_UP);
		} else if (MDiscountSchemaLine.LIST_ROUNDING_Nickel051015.equals(rounding)) {
			BigDecimal mm = new BigDecimal(20);
			roundAmt = roundAmt.multiply(mm); 
			roundAmt = roundAmt.setScale(0, RoundingMode.HALF_UP);
			roundAmt = roundAmt.divide(mm, 2, RoundingMode.HALF_UP);
		} else if (MDiscountSchemaLine.LIST_ROUNDING_NoRounding.equals(rounding)) {
			;
		} else if (MDiscountSchemaLine.LIST_ROUNDING_Quarter255075.equals(rounding)) {
			BigDecimal mm = new BigDecimal(4);
			roundAmt = roundAmt.multiply(mm); 
			roundAmt = roundAmt.setScale(0, RoundingMode.HALF_UP);
			roundAmt = roundAmt.divide(mm, 2, RoundingMode.HALF_UP);
		} else if (MDiscountSchemaLine.LIST_ROUNDING_Ten10002000.equals(rounding)) {
			roundAmt = roundAmt.setScale(-1, RoundingMode.HALF_UP);
		} else if (MDiscountSchemaLine.LIST_ROUNDING_Thousand.equals(rounding)) {
			roundAmt = roundAmt.setScale(-3, RoundingMode.HALF_UP);
		} else if (MDiscountSchemaLine.LIST_ROUNDING_WholeNumber00.equals(rounding)) {
			roundAmt = roundAmt.setScale(0, RoundingMode.HALF_UP);
		}
		roundAmt = roundAmt.subtract(grandTotal);
		return roundAmt;
	}

} // EventHandler
