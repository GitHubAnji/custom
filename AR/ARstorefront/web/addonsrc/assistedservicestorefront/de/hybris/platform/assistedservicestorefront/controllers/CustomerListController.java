/*
 * [y] hybris Platform
 *
 * Copyright (c) 2000-2016 hybris AG
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of hybris
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with hybris.
 *
 *
 */
package de.hybris.platform.assistedservicestorefront.controllers;

import de.hybris.platform.acceleratorstorefrontcommons.controllers.pages.AbstractSearchPageController;
import de.hybris.platform.assistedservicefacades.AssistedServiceFacade;
import de.hybris.platform.commercefacades.customer.CustomerListFacade;
import de.hybris.platform.commercefacades.user.data.CustomerData;
import de.hybris.platform.commercefacades.user.data.UserGroupData;
import de.hybris.platform.commerceservices.search.pagedata.PageableData;
import de.hybris.platform.commerceservices.search.pagedata.SearchPageData;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;


/**
 *
 * Controller to handle querying requests for ASM and handling customer lists implementations
 *
 */
@Controller
@RequestMapping("/assisted-service-querying")
public class CustomerListController extends AbstractSearchPageController
{
	private static final Logger LOG = Logger.getLogger(CustomerListController.class);

	private static String DEFAULT_CUSTOMER_LIST = "defaultList";
	private static String AVAILABLE_CUSTOMER_LIST = "availableLists";


	@Resource(name = "customerListFacade")
	private CustomerListFacade customerListFacade;

	@Resource(name = "assistedServiceFacade")
	private AssistedServiceFacade assistedServiceFacade;


	/**
	 * Method responsible for getting available customer list for agent and return a popup with the data
	 *
	 * @param model
	 *           model to hold the populated data
	 * @return the popup with list of customers list populated
	 */
	@RequestMapping(value = "/availableCustomerLists", method = RequestMethod.GET)
	public String getCustomersListPopup(final Model model, final HttpServletResponse response)
	{
		if (!assistedServiceFacade.isAssistedServiceAgentLoggedIn())
		{
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			assistedServiceFacade.getAsmSession().setFlashErrorMessage("asm.emulate.error.agent_missed");
			return null;
		}

		final List<UserGroupData> customerLists = customerListFacade.getCustomerListsForEmployee(assistedServiceFacade
				.getAsmSession().getAgent().getUid());

		// Handle paged search results
		if (!CollectionUtils.isEmpty(customerLists))
		{
			model.addAttribute(AVAILABLE_CUSTOMER_LIST, customerLists);
			model.addAttribute(DEFAULT_CUSTOMER_LIST, customerLists.get(0).getUid());
		}

		return AssistedservicestorefrontControllerConstants.Views.Fragments.CustomerListComponent.ASMCustomerListPopup;
	}

	/**
	 * Responsible for getting list of customers based on a customer List UId and handle pagination and sorting of this
	 * list as well
	 *
	 * @param model
	 *           to hold populated data
	 * @param page
	 *           page number in case we have more than 1 page of data
	 * @param showMode
	 *           either to show all or to show pages (default is page)
	 * @param sortCode
	 *           the sort code for the list of customers
	 * @param customerListUId
	 *           the customer list UId to get customers for
	 * @return paginated view with customer data
	 */
	@RequestMapping(value = "/listCustomers", method = RequestMethod.GET)
	public String listPaginatedCustomers(final Model model, @RequestParam(value = "page", defaultValue = "0") final int page,
			@RequestParam(value = "show", defaultValue = "Page") final ShowMode showMode,
			@RequestParam(value = "sort", required = false) final String sortCode,
			@RequestParam(value = "customerListUId", required = false) final String customerListUId)
	{
		try
		{
			if (!StringUtils.isBlank(customerListUId))
			{
				// Handle paged search results
				final PageableData pageableData = createPageableData(page, 5, sortCode, showMode);
				final SearchPageData<CustomerData> searchPageData = fetchSpecificCustomerList(customerListUId, pageableData);

				populateModel(model, searchPageData, showMode);
				model.addAttribute(DEFAULT_CUSTOMER_LIST, customerListUId);
			}
			else
			{
				throw new IllegalArgumentException("customerListUId can not be empty!");
			}
		}
		catch (final Exception exp)
		{
			LOG.error(exp);
		}
		return AssistedservicestorefrontControllerConstants.Views.Fragments.CustomerListComponent.ASMCustomerListTable;
	}

	protected SearchPageData<CustomerData> fetchSpecificCustomerList(final String customerListUid, final PageableData pageableData)
	{
		final Map<String, Object> parametersMap = new HashMap<>();

		return customerListFacade.getPagedCustomersForCustomerListUID(customerListUid, assistedServiceFacade.getAsmSession()
				.getAgent().getUid(), pageableData, parametersMap);
	}

	@Override
	protected void populateModel(final Model model, final SearchPageData<?> searchPageData, final ShowMode showMode)
	{
		final int numberPagesShown = 5;

		model.addAttribute("numberPagesShown", Integer.valueOf(numberPagesShown));
		model.addAttribute("searchPageData", searchPageData);
		model.addAttribute("isShowAllAllowed", calculateShowAll(searchPageData, showMode));
		model.addAttribute("isShowPageAllowed", calculateShowPaged(searchPageData, showMode));
	}

	@Override
	protected PageableData createPageableData(final int pageNumber, final int pageSize, final String sortCode,
			final ShowMode showMode)
	{
		final PageableData pageableData = new PageableData();
		pageableData.setCurrentPage(pageNumber);
		pageableData.setSort(sortCode);

		if (ShowMode.All == showMode)
		{
			pageableData.setPageSize(100);
		}
		else
		{
			pageableData.setPageSize(pageSize);
		}
		return pageableData;
	}

	@Override
	protected Boolean calculateShowAll(final SearchPageData<?> searchPageData, final ShowMode showMode)
	{
		return Boolean.valueOf((showMode != ShowMode.All && //
				searchPageData.getPagination().getTotalNumberOfResults() > searchPageData.getPagination().getPageSize())
				&& isShowAllAllowed(searchPageData));
	}

	@Override
	protected Boolean calculateShowPaged(final SearchPageData<?> searchPageData, final ShowMode showMode)
	{
		return Boolean
				.valueOf(showMode == ShowMode.All
						&& (searchPageData.getPagination().getNumberOfPages() > 1 || searchPageData.getPagination().getPageSize() == getMaxSearchPageSize()));
	}
}
