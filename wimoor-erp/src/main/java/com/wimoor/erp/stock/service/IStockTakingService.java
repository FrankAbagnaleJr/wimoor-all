package com.wimoor.erp.stock.service;


import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.wimoor.common.mvc.BizException;
import com.wimoor.common.user.UserInfo;
import com.wimoor.erp.stock.pojo.entity.StockTaking;
import com.wimoor.erp.stock.pojo.entity.StockTakingItem;

public interface IStockTakingService extends IService<StockTaking> {

	StockTaking saveStockTakingAndItem(StockTaking stockTaking, StockTakingItem item, boolean isnew) throws BizException ;

	List<Map<String, Object>> getItemList(String id, String warehouseid,String search);

	Map<String, Object> getSumQuantity(String id, String warehouseid);

	boolean endAction(StockTaking stocktaking, String warehouseid, UserInfo user) throws BizException;

	StockTaking  updateStockTakingAndItem(String id, String itemid) throws BizException;
	IPage<Map<String, Object>> findByCondition(Page<?> page,Map<String, Object> map);

	boolean cancelAction(StockTaking stocktaking, String warehouseid) throws BizException;

	Map<String, Object> selectAllStocktakingBySKU(String warehouseid);
	
	List<Map<String, Object>> findStockInvByParentId(String wid);

}
