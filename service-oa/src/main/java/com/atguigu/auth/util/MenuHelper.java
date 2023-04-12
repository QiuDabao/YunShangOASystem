package com.atguigu.auth.util;

import com.atguigu.model.system.SysMenu;

import java.util.ArrayList;
import java.util.List;

public class MenuHelper {
    //用递归方法建菜单
    public static List<SysMenu> buildTree(List<SysMenu> sysMenusList) {
        ArrayList<SysMenu> trees = new ArrayList<>();
        for(SysMenu sysMenu:sysMenusList){
            //parentId==0是入口
            if (sysMenu.getParentId().longValue() == 0) {
                trees.add(getChildren(sysMenu,sysMenusList));
            }
        }
        return trees;
    }

    private static SysMenu getChildren(SysMenu sysMenu,
                                       List<SysMenu> sysMenusList) {
        sysMenu.setChildren(new ArrayList<SysMenu>());
        //判断id和parentId的对应关系
        for (SysMenu it : sysMenusList){
            if(sysMenu.getId().longValue()==it.getParentId().longValue()){
                if (sysMenu.getChildren() == null) {
                    sysMenu.setChildren(new ArrayList<>());
                }
                sysMenu.getChildren().add(getChildren(it,sysMenusList));
            }
        }
        return sysMenu;
    }

}
