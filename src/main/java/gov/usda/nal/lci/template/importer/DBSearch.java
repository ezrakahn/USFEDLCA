package gov.usda.nal.lci.template.importer;
/** ===========================================================================
*
*                            PUBLIC DOMAIN NOTICE
*               		National Agriculture Library
*
*  This software/database is a "United States Government Work" under the
*  terms of the United States Copyright Act.  It was written as part of
*  the author's official duties as a United States Government employee and
*  thus cannot be copyrighted.  This software/database is freely available
*  to the public for use. The National Agriculture Library and the U.S.
*  Government have not placed any restriction on its use or reproduction.
*
*  Although all reasonable efforts have been taken to ensure the accuracy
*  and reliability of the software and data, the NAL and the U.S.
*  Government do not and cannot warrant the performance or results that
*  may be obtained by using this software or data. The NAL and the U.S.
*  Government disclaim all warranties, express or implied, including
*  warranties of performance, merchantability or fitness for any particular
*  purpose.
*
*  Please cite the author in any work or product based on this material.
*
*===========================================================================
*/
import gov.usda.nal.lci.template.domain.Person;



import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.openlca.core.database.IDatabase;
import org.openlca.core.database.Query;
import org.openlca.core.model.Actor;
import org.openlca.core.model.Category;
import org.openlca.core.model.Flow;
import org.openlca.core.model.FlowType;
import org.openlca.core.model.Location;
import org.openlca.core.model.Source;
import org.openlca.io.UnitMappingEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Search for EcoSpold entities in the database. */
class DBSearch {

	private IDatabase database;
	private Logger log = LoggerFactory.getLogger(getClass());

	public DBSearch(IDatabase database) {
		this.database = database;
	}

	public Actor findActor(Person person) {
		try {
			log.trace("Search for actor {} in database", person.getName());
			String jpql = "select a from Actor a where a.name = :name "
					+ "and a.address = :address";
			Map<String, Object> args = new HashMap<String, Object>();
			args.put("name", person.getName());
			args.put("address", person.getAddress());
			return Query.on(database).getFirst(Actor.class, jpql, args);
		} catch (Exception e) {
			log.error("Failed to search for Actor", e);
			return null;
		}
	}

	public Source findSource(gov.usda.nal.lci.template.domain.Source insource) {
		try {
			log.trace("Search for source {} in database", insource.getFirstAuthor());
			String jpql = "select s from Source s where s.name = :name";
			Map<String, Object> args = new HashMap<String, Object>();
			int year = insource.getYear() != null ? Integer.parseInt(insource.getYear())
					: 0;
			args.put("name", insource.getFirstAuthor() + " " + year);
			Source candidate = Query.on(database).getFirst(Source.class, jpql,
					args);
			if (candidate == null)
				return null;
			if (Objects.equals(candidate.getTextReference(), insource.getTextReference()))
				return candidate;
			return null;
		} catch (Exception e) {
			log.error("Failed to search for Source", e);
			return null;
		}
	}

	public Location findLocation(String locationCode) {
		try {
			log.trace("Search for location {} in database", locationCode);
			String jpql = "select loc from Location loc "
					+ "where loc.code = :locationCode";
			return Query.on(database).getFirst(Location.class, jpql,
					Collections.singletonMap("locationCode", locationCode));
		} catch (Exception e) {
			log.error("Failed to search for Location", e);
			return null;
		}
	}

	public Flow findFlow(gov.usda.nal.lci.template.domain.Exchange exchange, UnitMappingEntry mapping) {
		List<Flow> candidates = findFlows(exchange.getFlowName());
		if (candidates == null || candidates.isEmpty())
			return null;
		for (Flow flow : candidates) {
			if (!sameFlowType(exchange, flow))
				continue;
			if (!hasUnit(flow, mapping))
				continue;
			if (!sameCategory(exchange.getCategory(),
					exchange.getSubCategory(), flow))
				continue;
			if (!sameLocation(exchange.getLocation(), flow))
				continue;
			return flow;
		}
		return null;
	}

/*	public Flow findFlow(DataSet dataSet, UnitMappingEntry mapping) {
		IReferenceFunction refFun = dataSet.getReferenceFunction();
		if (refFun == null)
			return null;
		List<Flow> candidates = findFlows(refFun.getName());
		if (candidates == null || candidates.isEmpty())
			return null;
		for (Flow flow : candidates) {
			if (!hasUnit(flow, mapping))
				continue;
			if (!sameCategory(refFun.getCategory(), refFun.getSubCategory(),
					flow))
				continue;
			String locationCode = dataSet.getGeography() == null ? null
					: dataSet.getGeography().getLocation();
			if (!sameLocation(locationCode, flow))
				continue;
			return flow;
		}
		return null;
	}
*/
	private List<Flow> findFlows(String name) {
		try {
			log.trace("Search for flow {} in database", name);
			String jpql = "select f from Flow f where f.name = :name";
			return Query.on(database).getAll(Flow.class, jpql,
					Collections.singletonMap("name", name));
		} catch (Exception e) {
			log.error("Flow search failed", e);
			return Collections.emptyList();
		}
	}
	
	private boolean sameFlowType(gov.usda.nal.lci.template.domain.Exchange exchange, Flow flow) {
		if (exchange.isElementaryFlow())
			return flow.getFlowType() == FlowType.ELEMENTARY_FLOW;
		return flow.getFlowType() != FlowType.ELEMENTARY_FLOW;
	}

	private boolean hasUnit(Flow flow, UnitMappingEntry mapping) {
		if (flow == null || mapping == null
				|| mapping.getFlowProperty() == null)
			return false;
		return flow.getFactor(mapping.getFlowProperty()) != null;
	}

	private boolean sameCategory(String categoryName, String subCategoryName,
			Flow flow) {
		try {
			Category category = flow.getCategory();
			if ( category == null && categoryName == null )
				return true;
			if (sameCategory(subCategoryName, category)) {
				if (subCategoryName == null)
					return true;
				return sameCategory(categoryName, category.getParentCategory());
			}
			return false;
		} catch (Exception e) {
			log.error("Failed to check categories");
			return false;
		}
	}

	private boolean sameCategory(String name, Category category) {
		if (category == null)
			return false;
		if (name == null)
			return StringUtils.equals(category.getRefId(),
					Flow.class.getCanonicalName());
		return StringUtils.equalsIgnoreCase(name, category.getName());
	}

	private boolean sameLocation(String locationCode, Flow flow) {
		if (locationCode == null || locationCode.equals("GLO"))
			return flow.getLocation() == null
					|| "GLO".equalsIgnoreCase(flow.getLocation().getCode());
		if (flow.getLocation() == null)
			return false;
		return StringUtils.equalsIgnoreCase(locationCode, flow.getLocation()
				.getCode());
	}
}
