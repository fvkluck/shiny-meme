(ns reminder.core
  (:require [reagent.core :as r]
            [reagent.react-native :as rn]
            [tick.core :as t]))

(def title-style {:font-size 50})

(defonce state (r/atom {:cue-time "08:30" 
                        :reminder-time "17:00"
                        :days [{:date "date"
                                :goals {1 {:text "This is my first goal"}
                                        2 {:text "This is my second goal"}}
                                :goal "please fill out your goal"}]}))

(defn logger [x]
  (do
  (doseq [k (js-keys x)]
    (do (js/console.log k) (js/console.log (aget x k))))
  x))

(defn press-button [e]
  (js/alert "hallo"))

(defn get-by-id [goals id]
  (->> goals
       (filter #(= (% id)))
       first))

(defn update-goal-text [day id new-text]
  (let [g (get-by-id (:goals day) id)]))

(defn cue-view [day]
  [rn/view {:style {:fles 1 :align-items "center" :justify-content "center"}}
   [rn/text {:style title-style} "Today is the day"]
   #_[rn/text (str "What do you want to do on " (t/day-of-week (:date @state)) " " (:date @state)) "?"]
   [rn/text "Please take a moment to consider what you want to achieve today."]
   (for [[g-id g] (:goals @day)]
     [rn/text-input {:key g-id :default-value (:text g)
                     :on-change-text #(swap! day assoc-in [:goals g-id :text] %)}])
   [rn/text-input {:default-value "something" :on-change-text #(swap! day assoc :goal %)}]])

(defn hello []
  [rn/view  {:style  {:flex 1 :align-items  "center" :justify-content  "center"}}
   [rn/button {:on-press press-button :title "press me"}]
   [cue-view (r/cursor state [:days 0])]])

(defn ^:export -main  [& args]
  (r/as-element  [hello]))
