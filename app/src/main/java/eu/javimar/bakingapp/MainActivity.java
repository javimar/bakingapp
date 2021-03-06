/**
 *
 * Baking app
 *
 * @author Javier Martín
 * @email: javimardeveloper@gmail.com
 * @link http://www.javimar.eu
 * @package eu.javimar.bakingapp
 * @version 1.0
 *
BSD 3-Clause License

Copyright (c) 2017 JaviMar
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

 * Redistributions of source code must retain the above copyright notice, this
list of conditions and the following disclaimer.

 * Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.

 * Neither the name of the copyright holder nor the names of its
contributors may be used to endorse or promote products derived from
this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

BAKING APP: Part of the Android Developer Nanodegree
Allow a user to select a recipe and see video-guided steps for how to complete it.
 */
package eu.javimar.bakingapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.graphics.Palette;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import eu.javimar.bakingapp.model.Recipe;
import eu.javimar.bakingapp.sync.LoaderIntentService;
import eu.javimar.bakingapp.sync.LoadingTasks;
import eu.javimar.bakingapp.view.RecipeListAdapter;

import static eu.javimar.bakingapp.utils.HelperUtils.isNetworkAvailable;
import static eu.javimar.bakingapp.utils.HelperUtils.showSnackbar;

public class MainActivity extends AppCompatActivity implements RecipeListAdapter.ListRecipeClickListener
{
    private static String LOG_TAG = MainActivity.class.getName();

    @BindView(R.id.toolbar_main) Toolbar mToolbarMain;
    @BindView(R.id.collapse_toolbar) CollapsingToolbarLayout collapsingToolbarLayout;
    @BindView(R.id.rv_recipes_main) RecyclerView mRecyclerViewRecipes;
    @BindView(R.id.tv_error_message_display) TextView mErrorMessageDisplay;
    @BindView(R.id.pb_loading_indicator) ProgressBar mLoadingIndicator;

    private RecipeListAdapter recipeListAdapter;

    public static String RECIPE_ITEM_PARCEABLE = "recipe_item_parceable";

    public static int sCardColor;

    // The Master List of Recipes where everything revolves around :-)
    public static List<Recipe> master_list = new ArrayList<>();

    // IntentService notification logic
    private RecipesLoadedReceiver receiver;
    private IntentFilter intentFilter;


    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ButterKnife.bind(this);

        // Dynamically register a BroadcastReceiver and create an intent-filter to notify loading
        registerReceiver();

        // loads the recipes via IntentService
        loadRecipes();

        // Toolbar logic
        setSupportActionBar(mToolbarMain);

        if(getResources().getBoolean(R.bool.isPhone)) // Only activate on phones
        {
            collapsingToolbarLayout.setTitle(getString(R.string.app_name));
            setToolbarDecoration();
        }
        else
        {
            getSupportActionBar().setTitle(R.string.app_name);
            collapsingToolbarLayout.setTitleEnabled(false);
        }

        // have different column count depending on the orientation (landscape or portrait),
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT)
        {
            mRecyclerViewRecipes.setLayoutManager(new GridLayoutManager(this, 1));
        }
        else
        {
            mRecyclerViewRecipes.setLayoutManager(new GridLayoutManager(this, 3));
        }
        mRecyclerViewRecipes.setHasFixedSize(true);
        // link recipe data with the Views and pass MainActivity as listener and a context
        recipeListAdapter = new RecipeListAdapter(this, this);
        // Setting the adapter attaches it to the RecyclerView in our layout
        mRecyclerViewRecipes.setAdapter(recipeListAdapter);
    }



    /** Provides Toolbar functionality for the main screen */
    private void setToolbarDecoration()
    {
        // get the palette from the image for the toolbar
        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.baking);
        Palette palette = Palette.from(bitmap).generate();
        Palette.Swatch vibrant = palette.getVibrantSwatch();
        Palette.Swatch darkVibrant = palette.getDarkVibrantSwatch();
        Palette.Swatch lightVibrant = palette.getLightVibrantSwatch();

        if(vibrant != null && darkVibrant != null && lightVibrant != null)
        {
            sCardColor = lightVibrant.getRgb();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            {
                Window window = getWindow();
                window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
                window.setStatusBarColor(darkVibrant.getRgb());
            }
            collapsingToolbarLayout.setContentScrimColor(vibrant.getRgb());
            collapsingToolbarLayout.setStatusBarScrimColor(darkVibrant.getRgb());
            collapsingToolbarLayout.setCollapsedTitleTextColor(vibrant.getTitleTextColor());
        }
    }


    @Override
    public void onRecipeItemClick(Recipe recipe)
    {
        Intent i = new Intent(this, RecipeDetailViewActivity.class);
        i.putExtra(RECIPE_ITEM_PARCEABLE, recipe);
        startActivity(i);
    }


    private void loadRecipes()
    {
        if(isNetworkAvailable(this))
        {
            // load recipes from the internet if we have not already
            if(master_list == null || master_list.size() < 1)
            {
                Intent loadRecipesIntent =
                        new Intent(getApplicationContext(), LoaderIntentService.class);
                loadRecipesIntent.setAction(LoadingTasks.ACTION_LOAD_RECIPES);
                startService(loadRecipesIntent);
            }
        }
        else
        {
            mLoadingIndicator.setVisibility(View.GONE);
            showSnackbar(this, findViewById(android.R.id.content),
                    getString(R.string.no_internet_connection));
        }
    }


    public class RecipesLoadedReceiver extends BroadcastReceiver
    {
        public static final String RECIPES_LOADED_RESPONSE =
                "eu.javimar.bakingapp.intent.action.RECIPES_LOADED_RESPONSE";

        @Override
        public void onReceive(Context context, Intent intent) {
            // we have everything loaded, hopefully
            mLoadingIndicator.setVisibility(View.GONE);
            if(master_list == null || master_list.size() < 1)
            {
                mErrorMessageDisplay.setVisibility(View.VISIBLE);
            }
            else
            {
                recipeListAdapter.notifyDataSetChanged();
            }
        }
    }


    private void registerReceiver()
    {
        intentFilter = new IntentFilter(RecipesLoadedReceiver.RECIPES_LOADED_RESPONSE);
        intentFilter.addCategory(Intent.CATEGORY_DEFAULT);
        receiver = new RecipesLoadedReceiver();
        registerReceiver(receiver, intentFilter);
    }


    private void unregisterReceiver()
    {
        if (receiver!= null) this.unregisterReceiver(receiver);
        receiver = null;
        intentFilter = null;
    }

    @Override
    public void onStart()
    {
        // When rotating screen we need to hide the progress bar
        if(!master_list.isEmpty())
        {
            mLoadingIndicator.setVisibility(View.GONE);
        }
        // Register mReceiver when starting activity
        if (receiver == null && intentFilter== null) registerReceiver();
        super.onStart();
    }

    @Override
    public void onStop()
    {
        // unregister mReceiver when stopping activity
        unregisterReceiver();
        super.onStop();
    }


    @Override
    public void onDestroy()
    {
        // unregister mReceiver when destroying activity
        unregisterReceiver();
        super.onDestroy();
    }
}
